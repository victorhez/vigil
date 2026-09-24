use anchor_lang::prelude::*;

#[event]
pub struct VaultCreated {
    pub vault: Pubkey,
    pub owner: Pubkey,
    pub interval: i64,
    pub grace: i64,
    pub heir_count: u8,
}

#[event]
pub struct Pulsed {
    pub vault: Pubkey,
    pub by_owner: bool,
    pub timestamp: i64,
    pub streak: u32,
    pub deadline: i64,
}

#[event]
pub struct VaultConfigured {
    pub vault: Pubkey,
    pub interval: i64,
    pub grace: i64,
    pub heir_count: u8,
}

#[event]
pub struct PulseKeyRotated {
    pub vault: Pubkey,
    pub pulse_key: Pubkey,
}

#[event]
pub struct Released {
    pub vault: Pubkey,
    /// `Pubkey::default()` for native SOL.
    pub mint: Pubkey,
    pub amount: u64,
    pub caller: Pubkey,
}
