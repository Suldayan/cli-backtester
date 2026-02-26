pub fn calc_sma(prices: &[f64], window: usize, out: &mut [f64]) {
    let n = prices.len();
    debug_assert_eq!(n, out.len());
    debug_assert!(window > 0);

    if n < window {
        out.fill(f64::NAN);
        return;
    }

    let warmup = window - 1;
    out[..warmup].fill(f64::NAN);

    // Initialize with first window sum
    let mut sum: f64 = prices[..window].iter().sum();
    out[warmup] = sum / window as f64;

    // Rolling window
    for i in window..n {
        sum += prices[i];
        sum -= prices[i - window];
        out[i] = sum / window as f64;
    }
}

pub fn calc_ema(prices: &[f64], window: usize, out: &mut [f64]) {
    let n = prices.len();
    debug_assert_eq!(n, out.len());
    debug_assert!(window > 0);

    if n < window {
        out.fill(f64::NAN);
        return;
    }

    let alpha = 2.0 / (window as f64 + 1.0);
    let inv_alpha = 1.0 - alpha;
    let warmup = window - 1;

    out[..warmup].fill(f64::NAN);

    // Seed with SMA
    let sum: f64 = prices[..window].iter().sum();
    let mut prev_ema = sum / window as f64;
    out[warmup] = prev_ema;

    for i in window..n {
        prev_ema = prices[i] * alpha + prev_ema * inv_alpha;
        out[i] = prev_ema;
    }
}

