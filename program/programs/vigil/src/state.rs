use anchor_lang::prelude::*;

use crate::errors::VigilError;

pub const VAULT_SEED: &[u8] = b"vault";
pub const MAX_HEIRS: usize = 5;
pub const BPS_DENOMINATOR: u64 = 10_000;
pub const SECONDS_PER_DAY: i64 = 86_400;

/// Shortest allowed check-in interval. Kept low so a vault can be exercised end-to-end in minutes.
pub const MIN_INTERVAL: i64 = 60;
/// Longest allowed check-in interval (~5 years).
pub const MAX_INTERVAL: i64 = 5 * 365 * SECONDS_PER_DAY;
/// Longest allowed grace period after a missed check-in (1 year).
pub const MAX_GRACE: i64 = 365 * SECONDS_PER_DAY;

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Default, PartialEq, Eq, InitSpace, Debug)]
pub struct Heir {
    pub wallet: Pubkey,
    pub share_bps: u16,
}

/// One vault per owner. Holds SOL directly in its own lamports and owns token accounts for SPL assets.
///
/// Lifecycle:
///   Alive    -> now <= last_pulse + interval
///   Overdue  -> last_pulse + interval < now <= last_pulse + interval + grace
///   Expired  -> now > last_pulse + interval + grace (anyone may release assets to heirs)
///   Released -> at least one release happened; the vault is sealed for its owner.
#[account]
#[derive(InitSpace)]
pub struct Vault {
    /// Wallet with full control: configure, withdraw, rotate the pulse key, close.
    pub owner: Pubkey,
    /// Device-bound delegate that may only check in. It can never move funds.
    pub pulse_key: Pubkey,
    /// Seconds allowed between check-ins.
    pub interval: i64,
    /// Extra seconds after a missed check-in before heirs can claim.
    pub grace: i64,
    pub created_at: i64,
    pub last_pulse: i64,
    /// Consecutive UTC days with at least one check-in.
    pub streak: u32,
    pub best_streak: u32,
    pub total_pulses: u64,
    /// Unix timestamp of the first release, 0 while the vault is live.
    pub released_at: i64,
    pub heir_count: u8,
    pub heirs: [Heir; MAX_HEIRS],
    pub bump: u8,
}

impl Vault {
    pub fn deadline(&self) -> Result<i64> {
        self.last_pulse
            .checked_add(self.interval)
            .and_then(|t| t.checked_add(self.grace))
            .ok_or_else(|| error!(VigilError::MathOverflow))
    }

    pub fn is_expired(&self, now: i64) -> Result<bool> {
        Ok(now > self.deadline()?)
    }

    pub fn is_released(&self) -> bool {
        self.released_at != 0
    }

    pub fn active_heirs(&self) -> &[Heir] {
        &self.heirs[..self.heir_count as usize]
    }

    /// Records a proof of life and advances the daily streak.
    pub fn record_pulse(&mut self, now: i64) -> Result<()> {
        let today = now.div_euclid(SECONDS_PER_DAY);
        let last_day = self.last_pulse.div_euclid(SECONDS_PER_DAY);

        if self.total_pulses == 0 || today > last_day + 1 {
            self.streak = 1;
        } else if today == last_day + 1 {
            self.streak = self.streak.saturating_add(1);
        }

        self.best_streak = self.best_streak.max(self.streak);
        self.total_pulses = self.total_pulses.saturating_add(1);
        self.last_pulse = now;
        Ok(())
    }

    pub fn set_schedule(&mut self, interval: i64, grace: i64) -> Result<()> {
        require!(
            (MIN_INTERVAL..=MAX_INTERVAL).contains(&interval),
            VigilError::InvalidInterval
        );
        require!((0..=MAX_GRACE).contains(&grace), VigilError::InvalidGrace);
        self.interval = interval;
        self.grace = grace;
        Ok(())
    }

    pub fn set_heirs(&mut self, vault_key: &Pubkey, heirs: &[Heir]) -> Result<()> {
        require!(
            !heirs.is_empty() && heirs.len() <= MAX_HEIRS,
            VigilError::InvalidHeirCount
        );

        let mut total: u64 = 0;
        for (i, heir) in heirs.iter().enumerate() {
            require!(heir.wallet != Pubkey::default(), VigilError::InvalidHeir);
            require!(heir.wallet != *vault_key, VigilError::InvalidHeir);
            require!(heir.share_bps > 0, VigilError::InvalidShare);
            require!(
                heirs[..i].iter().all(|h| h.wallet != heir.wallet),
                VigilError::DuplicateHeir
            );
            total += heir.share_bps as u64;
        }
        require!(total == BPS_DENOMINATOR, VigilError::SharesMustTotal100);

        self.heirs = [Heir::default(); MAX_HEIRS];
        self.heirs[..heirs.len()].copy_from_slice(heirs);
        self.heir_count = heirs.len() as u8;
        Ok(())
    }

    /// Splits `amount` across heirs by share. Rounding dust goes to the last heir so nothing is stranded.
    pub fn split(&self, amount: u64) -> Result<Vec<u64>> {
        let heirs = self.active_heirs();
        let mut parts = Vec::with_capacity(heirs.len());
        let mut assigned: u64 = 0;
        for (i, heir) in heirs.iter().enumerate() {
            let part = if i + 1 == heirs.len() {
                amount - assigned
            } else {
                ((amount as u128 * heir.share_bps as u128) / BPS_DENOMINATOR as u128) as u64
            };
            assigned = assigned
                .checked_add(part)
                .ok_or_else(|| error!(VigilError::MathOverflow))?;
            parts.push(part);
        }
        Ok(parts)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn vault() -> Vault {
        Vault {
            owner: Pubkey::new_unique(),
            pulse_key: Pubkey::new_unique(),
            interval: SECONDS_PER_DAY,
            grace: SECONDS_PER_DAY,
            created_at: 0,
            last_pulse: 0,
            streak: 0,
            best_streak: 0,
            total_pulses: 0,
            released_at: 0,
            heir_count: 0,
            heirs: [Heir::default(); MAX_HEIRS],
            bump: 255,
        }
    }

    fn heir(bps: u16) -> Heir {
        Heir { wallet: Pubkey::new_unique(), share_bps: bps }
    }

    #[test]
    fn streak_counts_consecutive_days() {
        let mut v = vault();
        let day = SECONDS_PER_DAY;
        v.record_pulse(10 * day + 100).unwrap();
        assert_eq!(v.streak, 1);
        v.record_pulse(10 * day + 5_000).unwrap(); // same day
        assert_eq!(v.streak, 1);
        v.record_pulse(11 * day + 1).unwrap();
        v.record_pulse(12 * day + 1).unwrap();
        assert_eq!(v.streak, 3);
        v.record_pulse(14 * day + 1).unwrap(); // skipped a day
        assert_eq!(v.streak, 1);
        assert_eq!(v.best_streak, 3);
        assert_eq!(v.total_pulses, 5);
    }

    #[test]
    fn deadline_and_expiry() {
        let mut v = vault();
        v.record_pulse(1_000).unwrap();
        assert_eq!(v.deadline().unwrap(), 1_000 + 2 * SECONDS_PER_DAY);
        assert!(!v.is_expired(1_000 + 2 * SECONDS_PER_DAY).unwrap());
        assert!(v.is_expired(1_001 + 2 * SECONDS_PER_DAY).unwrap());
    }

    #[test]
    fn heirs_must_total_exactly_100_percent() {
        let key = Pubkey::new_unique();
        let mut v = vault();
        assert!(v.set_heirs(&key, &[heir(5_000), heir(4_999)]).is_err());
        assert!(v.set_heirs(&key, &[heir(5_000), heir(5_001)]).is_err());
        assert!(v.set_heirs(&key, &[heir(6_000), heir(4_000)]).is_ok());
        assert_eq!(v.heir_count, 2);
    }

    #[test]
    fn rejects_bad_heir_lists() {
        let key = Pubkey::new_unique();
        let mut v = vault();
        assert!(v.set_heirs(&key, &[]).is_err());
        assert!(v.set_heirs(&key, &[heir(2_000); 6]).is_err());
        let dup = heir(5_000);
        assert!(v.set_heirs(&key, &[dup, dup]).is_err());
        assert!(v.set_heirs(&key, &[Heir { wallet: key, share_bps: 10_000 }]).is_err());
        assert!(v.set_heirs(&key, &[Heir { wallet: Pubkey::default(), share_bps: 10_000 }]).is_err());
        assert!(v.set_heirs(&key, &[heir(10_000), heir(0)]).is_err());
    }

    #[test]
    fn split_is_exact_and_dust_goes_to_last_heir() {
        let key = Pubkey::new_unique();
        let mut v = vault();
        v.set_heirs(&key, &[heir(3_333), heir(3_333), heir(3_334)]).unwrap();
        let parts = v.split(1_000_000_001).unwrap();
        assert_eq!(parts.iter().sum::<u64>(), 1_000_000_001);
        assert_eq!(parts[0], 333_300_000);
        assert_eq!(parts[1], 333_300_000);
        assert_eq!(parts[2], 333_400_001);
    }

    #[test]
    fn split_handles_max_amount() {
        let key = Pubkey::new_unique();
        let mut v = vault();
        v.set_heirs(&key, &[heir(1), heir(9_999)]).unwrap();
        let parts = v.split(u64::MAX).unwrap();
        assert_eq!(parts.iter().map(|p| *p as u128).sum::<u128>(), u64::MAX as u128);
    }

    #[test]
    fn schedule_bounds() {
        let mut v = vault();
        assert!(v.set_schedule(MIN_INTERVAL - 1, 0).is_err());
        assert!(v.set_schedule(MAX_INTERVAL + 1, 0).is_err());
        assert!(v.set_schedule(MIN_INTERVAL, -1).is_err());
        assert!(v.set_schedule(MIN_INTERVAL, MAX_GRACE + 1).is_err());
        assert!(v.set_schedule(SECONDS_PER_DAY, MAX_GRACE).is_ok());
    }

    #[test]
    fn account_layout_matches_client() {
        // The Android client decodes this layout by offset; keep them in sync.
        assert_eq!(8 + Vault::INIT_SPACE, 300);
    }
}
