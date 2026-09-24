use anchor_lang::prelude::*;

#[error_code]
pub enum VigilError {
    #[msg("Check-in interval is outside the allowed range")]
    InvalidInterval,
    #[msg("Grace period is outside the allowed range")]
    InvalidGrace,
    #[msg("A vault needs between one and five heirs")]
    InvalidHeirCount,
    #[msg("Heir wallet is not allowed")]
    InvalidHeir,
    #[msg("Each heir must receive a non-zero share")]
    InvalidShare,
    #[msg("The same wallet is listed twice")]
    DuplicateHeir,
    #[msg("Heir shares must add up to exactly 100%")]
    SharesMustTotal100,
    #[msg("Signer is neither the owner nor the registered pulse key")]
    UnauthorizedPulse,
    #[msg("The vault has expired; only the owner wallet can revive it")]
    PulseKeyExpired,
    #[msg("The vault has been released to its heirs")]
    AlreadyReleased,
    #[msg("The vault is still alive")]
    NotExpired,
    #[msg("Heir accounts do not match the vault's heir list")]
    HeirMismatch,
    #[msg("Nothing to release")]
    NothingToRelease,
    #[msg("Withdrawal would leave the vault below its rent-exempt minimum")]
    InsufficientFunds,
    #[msg("Arithmetic overflow")]
    MathOverflow,
}
