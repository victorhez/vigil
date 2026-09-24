//! Owner-only instructions. Every one of them also counts as a check-in:
//! a signature from the owner's wallet is the strongest proof of life there is.

use anchor_lang::prelude::*;
use anchor_lang::system_program;
use anchor_spl::token_interface::{
    self, Mint, TokenAccount, TokenInterface, TransferChecked,
};

use crate::errors::VigilError;
use crate::events::{PulseKeyRotated, VaultConfigured};
use crate::state::{Heir, Vault, VAULT_SEED};

#[derive(Accounts)]
pub struct OwnerOnly<'info> {
    /// Writable because `withdraw` credits lamports straight back to the owner.
    #[account(mut)]
    pub owner: Signer<'info>,

    #[account(
        mut,
        has_one = owner,
        seeds = [VAULT_SEED, owner.key().as_ref()],
        bump = vault.bump,
    )]
    pub vault: Account<'info, Vault>,
}

pub fn configure(
    ctx: Context<OwnerOnly>,
    interval: i64,
    grace: i64,
    heirs: Vec<Heir>,
) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let vault_key = ctx.accounts.vault.key();
    let vault = &mut ctx.accounts.vault;
    require!(!vault.is_released(), VigilError::AlreadyReleased);

    vault.set_schedule(interval, grace)?;
    vault.set_heirs(&vault_key, &heirs)?;
    vault.record_pulse(now)?;

    emit!(VaultConfigured {
        vault: vault_key,
        interval,
        grace,
        heir_count: vault.heir_count,
    });
    Ok(())
}

pub fn rotate_pulse_key(ctx: Context<OwnerOnly>, pulse_key: Pubkey) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let vault_key = ctx.accounts.vault.key();
    let vault = &mut ctx.accounts.vault;
    require!(!vault.is_released(), VigilError::AlreadyReleased);

    vault.pulse_key = pulse_key;
    vault.record_pulse(now)?;

    emit!(PulseKeyRotated { vault: vault_key, pulse_key });
    Ok(())
}

#[derive(Accounts)]
pub struct Deposit<'info> {
    /// Anyone may top up a vault; only the owner's deposits count as a check-in.
    #[account(mut)]
    pub depositor: Signer<'info>,

    #[account(
        mut,
        seeds = [VAULT_SEED, vault.owner.as_ref()],
        bump = vault.bump,
    )]
    pub vault: Account<'info, Vault>,

    pub system_program: Program<'info, System>,
}

pub fn deposit(ctx: Context<Deposit>, amount: u64) -> Result<()> {
    system_program::transfer(
        CpiContext::new(
            ctx.accounts.system_program.key(),
            system_program::Transfer {
                from: ctx.accounts.depositor.to_account_info(),
                to: ctx.accounts.vault.to_account_info(),
            },
        ),
        amount,
    )?;

    let vault = &mut ctx.accounts.vault;
    if ctx.accounts.depositor.key() == vault.owner && !vault.is_released() {
        vault.record_pulse(Clock::get()?.unix_timestamp)?;
    }
    Ok(())
}

pub fn withdraw(ctx: Context<OwnerOnly>, amount: u64) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    require!(!ctx.accounts.vault.is_released(), VigilError::AlreadyReleased);

    let vault_info = ctx.accounts.vault.to_account_info();
    let owner_info = ctx.accounts.owner.to_account_info();
    let rent_floor = Rent::get()?.minimum_balance(vault_info.data_len());
    let remaining = vault_info
        .lamports()
        .checked_sub(amount)
        .ok_or_else(|| error!(VigilError::InsufficientFunds))?;
    require!(remaining >= rent_floor, VigilError::InsufficientFunds);

    **vault_info.try_borrow_mut_lamports()? = remaining;
    **owner_info.try_borrow_mut_lamports()? = owner_info
        .lamports()
        .checked_add(amount)
        .ok_or_else(|| error!(VigilError::MathOverflow))?;

    ctx.accounts.vault.record_pulse(now)?;
    Ok(())
}

#[derive(Accounts)]
pub struct WithdrawToken<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,

    #[account(
        mut,
        has_one = owner,
        seeds = [VAULT_SEED, owner.key().as_ref()],
        bump = vault.bump,
    )]
    pub vault: Account<'info, Vault>,

    pub mint: InterfaceAccount<'info, Mint>,

    #[account(
        mut,
        token::mint = mint,
        token::authority = vault,
        token::token_program = token_program,
    )]
    pub vault_tokens: InterfaceAccount<'info, TokenAccount>,

    #[account(
        mut,
        token::mint = mint,
        token::token_program = token_program,
    )]
    pub destination: InterfaceAccount<'info, TokenAccount>,

    pub token_program: Interface<'info, TokenInterface>,
}

pub fn withdraw_token(ctx: Context<WithdrawToken>, amount: u64) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    require!(!ctx.accounts.vault.is_released(), VigilError::AlreadyReleased);

    let owner_key = ctx.accounts.owner.key();
    let bump = [ctx.accounts.vault.bump];
    let seeds: &[&[u8]] = &[VAULT_SEED, owner_key.as_ref(), &bump];

    token_interface::transfer_checked(
        CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            TransferChecked {
                from: ctx.accounts.vault_tokens.to_account_info(),
                mint: ctx.accounts.mint.to_account_info(),
                to: ctx.accounts.destination.to_account_info(),
                authority: ctx.accounts.vault.to_account_info(),
            },
            &[seeds],
        ),
        amount,
        ctx.accounts.mint.decimals,
    )?;

    ctx.accounts.vault.record_pulse(now)?;
    Ok(())
}

#[derive(Accounts)]
pub struct CloseVault<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,

    #[account(
        mut,
        has_one = owner,
        close = owner,
        seeds = [VAULT_SEED, owner.key().as_ref()],
        bump = vault.bump,
        constraint = !vault.is_released() @ VigilError::AlreadyReleased,
    )]
    pub vault: Account<'info, Vault>,
}
