use anchor_lang::prelude::*;

use crate::events::VaultCreated;
use crate::state::{Heir, Vault, VAULT_SEED};

#[derive(Accounts)]
pub struct CreateVault<'info> {
    #[account(mut)]
    pub owner: Signer<'info>,

    #[account(
        init,
        payer = owner,
        space = 8 + Vault::INIT_SPACE,
        seeds = [VAULT_SEED, owner.key().as_ref()],
        bump,
    )]
    pub vault: Account<'info, Vault>,

    pub system_program: Program<'info, System>,
}

pub fn create_vault(
    ctx: Context<CreateVault>,
    interval: i64,
    grace: i64,
    pulse_key: Pubkey,
    heirs: Vec<Heir>,
) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let vault_key = ctx.accounts.vault.key();
    let vault = &mut ctx.accounts.vault;

    vault.owner = ctx.accounts.owner.key();
    vault.pulse_key = pulse_key;
    vault.created_at = now;
    vault.released_at = 0;
    vault.bump = ctx.bumps.vault;
    vault.set_schedule(interval, grace)?;
    vault.set_heirs(&vault_key, &heirs)?;
    vault.record_pulse(now)?;

    emit!(VaultCreated {
        vault: vault_key,
        owner: vault.owner,
        interval,
        grace,
        heir_count: vault.heir_count,
    });
    Ok(())
}
