//! Permissionless release. Once a vault is past `last_pulse + interval + grace`,
//! anyone (an heir, a friend, a keeper bot) can push its assets to the heirs.
//! The destination of every lamport and token is fixed by the vault's heir list,
//! so the caller has no way to redirect funds.

use anchor_lang::prelude::*;
use anchor_spl::token_interface::{
    self, Mint, TokenAccount, TokenInterface, TransferChecked,
};

use crate::errors::VigilError;
use crate::events::Released;
use crate::state::{Vault, VAULT_SEED};

#[derive(Accounts)]
pub struct ReleaseSol<'info> {
    pub caller: Signer<'info>,

    #[account(
        mut,
        seeds = [VAULT_SEED, vault.owner.as_ref()],
        bump = vault.bump,
    )]
    pub vault: Account<'info, Vault>,
    // remaining_accounts: heir wallets, writable, in vault order.
}

pub fn release_sol<'info>(ctx: Context<'info, ReleaseSol<'info>>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let vault = &ctx.accounts.vault;
    require!(vault.is_expired(now)?, VigilError::NotExpired);

    let heirs = vault.active_heirs().to_vec();
    let heir_infos = ctx.remaining_accounts;
    require!(heir_infos.len() == heirs.len(), VigilError::HeirMismatch);
    for (info, heir) in heir_infos.iter().zip(heirs.iter()) {
        require!(
            info.key() == heir.wallet && info.is_writable,
            VigilError::HeirMismatch
        );
    }

    let vault_info = ctx.accounts.vault.to_account_info();
    let rent_floor = Rent::get()?.minimum_balance(vault_info.data_len());
    let available = vault_info.lamports().saturating_sub(rent_floor);
    require!(available > 0, VigilError::NothingToRelease);

    let parts = ctx.accounts.vault.split(available)?;
    **vault_info.try_borrow_mut_lamports()? = vault_info.lamports() - available;
    for (info, part) in heir_infos.iter().zip(parts) {
        **info.try_borrow_mut_lamports()? = info
            .lamports()
            .checked_add(part)
            .ok_or_else(|| error!(VigilError::MathOverflow))?;
    }

    let vault = &mut ctx.accounts.vault;
    if !vault.is_released() {
        vault.released_at = now;
    }

    emit!(Released {
        vault: vault.key(),
        mint: Pubkey::default(),
        amount: available,
        caller: ctx.accounts.caller.key(),
    });
    Ok(())
}

#[derive(Accounts)]
pub struct ReleaseToken<'info> {
    pub caller: Signer<'info>,

    #[account(
        mut,
        seeds = [VAULT_SEED, vault.owner.as_ref()],
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

    pub token_program: Interface<'info, TokenInterface>,
    // remaining_accounts: one token account per heir (mint = `mint`, owner = heir wallet), writable, in vault order.
}

pub fn release_token<'info>(
    ctx: Context<'info, ReleaseToken<'info>>,
) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let vault = &ctx.accounts.vault;
    require!(vault.is_expired(now)?, VigilError::NotExpired);

    let heirs = vault.active_heirs().to_vec();
    let heir_infos = ctx.remaining_accounts;
    require!(heir_infos.len() == heirs.len(), VigilError::HeirMismatch);

    let mint_key = ctx.accounts.mint.key();
    let token_program_key = ctx.accounts.token_program.key();
    for (info, heir) in heir_infos.iter().zip(heirs.iter()) {
        require!(info.is_writable, VigilError::HeirMismatch);
        require!(info.owner == &token_program_key, VigilError::HeirMismatch);
        let account = InterfaceAccount::<TokenAccount>::try_from(info)?;
        require!(
            account.owner == heir.wallet && account.mint == mint_key,
            VigilError::HeirMismatch
        );
    }

    let available = ctx.accounts.vault_tokens.amount;
    require!(available > 0, VigilError::NothingToRelease);
    let parts = vault.split(available)?;

    let owner_key = vault.owner;
    let bump = [vault.bump];
    let seeds: &[&[u8]] = &[VAULT_SEED, owner_key.as_ref(), &bump];
    let decimals = ctx.accounts.mint.decimals;

    for (info, part) in heir_infos.iter().zip(parts) {
        if part == 0 {
            continue;
        }
        token_interface::transfer_checked(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.vault_tokens.to_account_info(),
                    mint: ctx.accounts.mint.to_account_info(),
                    to: info.clone(),
                    authority: ctx.accounts.vault.to_account_info(),
                },
                &[seeds],
            ),
            part,
            decimals,
        )?;
    }

    let vault = &mut ctx.accounts.vault;
    if !vault.is_released() {
        vault.released_at = now;
    }

    emit!(Released {
        vault: vault.key(),
        mint: mint_key,
        amount: available,
        caller: ctx.accounts.caller.key(),
    });
    Ok(())
}
