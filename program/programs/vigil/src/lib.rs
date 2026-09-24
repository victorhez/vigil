//! Vigil: a proof-of-life vault.
//!
//! The owner checks in on a schedule they choose. If the check-ins stop for longer than
//! `interval + grace`, the vault's SOL and SPL tokens can be released to a fixed list of heirs.
//! Day-to-day check-ins are signed by a device-bound `pulse_key` that can never move funds.

use anchor_lang::prelude::*;

pub mod errors;
pub mod events;
pub mod instructions;
pub mod state;

use instructions::*;
use state::Heir;

declare_id!("VigddEZM9A4TuLmKFkY5qwVA5eCDDniEPJ4gmGQM512");

#[program]
pub mod vigil {
    use super::*;

    /// Opens a vault for the signing wallet and records the first check-in.
    pub fn create_vault(
        ctx: Context<CreateVault>,
        interval: i64,
        grace: i64,
        pulse_key: Pubkey,
        heirs: Vec<Heir>,
    ) -> Result<()> {
        instructions::create::create_vault(ctx, interval, grace, pulse_key, heirs)
    }

    /// Proof of life, signed by the owner or the device pulse key.
    pub fn pulse(ctx: Context<Pulse>) -> Result<()> {
        instructions::pulse::pulse(ctx)
    }

    /// Replaces the schedule and heir list.
    pub fn configure(
        ctx: Context<OwnerOnly>,
        interval: i64,
        grace: i64,
        heirs: Vec<Heir>,
    ) -> Result<()> {
        instructions::owner::configure(ctx, interval, grace, heirs)
    }

    /// Points check-ins at a new device key, e.g. after replacing a phone.
    pub fn rotate_pulse_key(ctx: Context<OwnerOnly>, pulse_key: Pubkey) -> Result<()> {
        instructions::owner::rotate_pulse_key(ctx, pulse_key)
    }

    pub fn deposit(ctx: Context<Deposit>, amount: u64) -> Result<()> {
        instructions::owner::deposit(ctx, amount)
    }

    pub fn withdraw(ctx: Context<OwnerOnly>, amount: u64) -> Result<()> {
        instructions::owner::withdraw(ctx, amount)
    }

    pub fn withdraw_token(ctx: Context<WithdrawToken>, amount: u64) -> Result<()> {
        instructions::owner::withdraw_token(ctx, amount)
    }

    /// Closes a live vault and returns its lamports to the owner.
    /// Token balances must be withdrawn first.
    pub fn close_vault(_ctx: Context<CloseVault>) -> Result<()> {
        Ok(())
    }

    /// Splits the vault's spare SOL across heirs. Callable by anyone once expired.
    pub fn release_sol<'info>(
        ctx: Context<'info, ReleaseSol<'info>>,
    ) -> Result<()> {
        instructions::release::release_sol(ctx)
    }

    /// Splits one token balance across heirs. Callable by anyone once expired.
    pub fn release_token<'info>(
        ctx: Context<'info, ReleaseToken<'info>>,
    ) -> Result<()> {
        instructions::release::release_token(ctx)
    }
}
