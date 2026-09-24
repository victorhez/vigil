use anchor_lang::prelude::*;

use crate::errors::VigilError;
use crate::events::Pulsed;
use crate::state::{Vault, VAULT_SEED};

#[derive(Accounts)]
pub struct Pulse<'info> {
    /// Either the owner wallet or the device-bound pulse key. Pays its own fee.
    pub authority: Signer<'info>,

    #[account(
        mut,
        seeds = [VAULT_SEED, vault.owner.as_ref()],
        bump = vault.bump,
    )]
    pub vault: Account<'info, Vault>,
}

pub fn pulse(ctx: Context<Pulse>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let vault_key = ctx.accounts.vault.key();
    let authority = ctx.accounts.authority.key();
    let vault = &mut ctx.accounts.vault;

    require!(!vault.is_released(), VigilError::AlreadyReleased);

    let by_owner = authority == vault.owner;
    require!(
        by_owner || authority == vault.pulse_key,
        VigilError::UnauthorizedPulse
    );
    // A lost or stolen phone must not be able to keep an expired vault alive.
    // Once expired, only a signature from the owner's wallet counts as proof of life.
    if !by_owner {
        require!(!vault.is_expired(now)?, VigilError::PulseKeyExpired);
    }

    vault.record_pulse(now)?;

    emit!(Pulsed {
        vault: vault_key,
        by_owner,
        timestamp: now,
        streak: vault.streak,
        deadline: vault.deadline()?,
    });
    Ok(())
}
