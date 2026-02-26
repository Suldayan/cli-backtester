use crate::indicators::{calc_sma, calc_ema};

pub fn calc_roc(prices: &[f64], period: usize, out: &mut [f64]) {
    let n = prices.len();
    debug_assert_eq!(n, out.len(), "Output buffer length mismatch");
    debug_assert!(period > 0, "Period must be > 0");

    let warmup = period.min(n);
    out[..warmup].fill(f64::NAN);

    for i in warmup..n {
        let prev_price = prices[i - period];
        if prev_price != 0.0 {
            out[i] = (prices[i] - prev_price) / prev_price * 100.0;
        } else {
            out[i] = 0.0;
        }
    }
}

pub fn calc_macd(
    prices: &[f64],
    fast: usize,
    slow: usize,
    signal: usize,
    out_macd: &mut [f64],
    out_signal: &mut [f64],
    out_hist: &mut [f64],
) {
    let n = prices.len();
    debug_assert_eq!(n, out_macd.len());
    debug_assert_eq!(n, out_signal.len());
    debug_assert_eq!(n, out_hist.len());
    debug_assert!(fast < slow, "Fast period must be < Slow period");

    {
        let temp_buf = &mut *out_hist;
        calc_ema(prices, slow, temp_buf);
    }

    calc_ema(prices, fast, out_macd);

    for i in 0..n {
        out_macd[i] -= out_hist[i];
    }

    // Signal Line (EMA of MACD)
    calc_ema_skip_nan(out_macd, signal, out_signal);

    for i in 0..n {
        out_hist[i] = out_macd[i] - out_signal[i];
    }
}

pub fn calc_stochastic(
    high: &[f64],
    low: &[f64],
    close: &[f64],
    lookback: usize,
    k_smoothing: usize,
    d_smoothing: usize,
    out_k: &mut [f64],
    out_d: &mut [f64],
) {
    let n = close.len();
    debug_assert_eq!(high.len(), n);
    debug_assert_eq!(low.len(), n);
    debug_assert_eq!(out_k.len(), n);
    debug_assert_eq!(out_d.len(), n);
    debug_assert!(lookback > 0 && k_smoothing > 0 && d_smoothing > 0);

    let mut raw_k = vec![f64::NAN; n];

    // Track min/max indices to avoid full window scans where possible
    let mut highest_idx = 0;
    let mut lowest_idx = 0;

    let start_idx = if lookback > 0 { lookback - 1 } else { 0 };

    for i in start_idx..n {
        let window_start = i + 1 - lookback;

        // Only rescan if the extrema fell out of the window
        if highest_idx < window_start {
            highest_idx = window_start;
            for j in (window_start + 1)..=i {
                if high[j] >= high[highest_idx] { highest_idx = j; }
            }
        } else if high[i] >= high[highest_idx] {
            highest_idx = i;
        }

        if lowest_idx < window_start {
            lowest_idx = window_start;
            for j in (window_start + 1)..=i {
                if low[j] <= low[lowest_idx] { lowest_idx = j; }
            }
        } else if low[i] <= low[lowest_idx] {
            lowest_idx = i;
        }

        let highest = high[highest_idx];
        let lowest = low[lowest_idx];
        let range = highest - lowest;

        raw_k[i] = if range == 0.0 {
            0.0
        } else {
            (close[i] - lowest) / range * 100.0
        };
    }

    calc_sma_skip_nan(&raw_k, k_smoothing, out_k);
    calc_sma_skip_nan(out_k, d_smoothing, out_d);
}

#[inline(always)]
fn calc_sma_skip_nan(prices: &[f64], window: usize, out: &mut [f64]) {
    debug_assert_eq!(out.len(), prices.len());

    let first_valid = prices.iter().position(|&x| x.is_finite());

    if let Some(idx) = first_valid {
        if idx + window <= prices.len() {
            out[0..idx].fill(f64::NAN);

            // Calculate SMA on the valid slice directly
            calc_sma(&prices[idx..], window, &mut out[idx..]);
            return;
        }
    }
    
    // Fallback: not enough data
    out.fill(f64::NAN);
}

#[inline(always)]
fn calc_ema_skip_nan(input: &[f64], window: usize, out: &mut [f64]) {
    let n = input.len();
    debug_assert_eq!(n, out.len());

    let first_valid_idx = input.iter().position(|&x| !x.is_nan());

    match first_valid_idx {
        Some(start) if n >= start + window => {
            out[0..start + window - 1].fill(f64::NAN);

            let alpha = 2.0 / (window as f64 + 1.0);
            let inv_alpha = 1.0 - alpha;

            // Seed EMA with SMA of the first window
            let sum: f64 = input[start..start + window].iter().sum();
            let mut prev_ema = sum / window as f64;
            
            out[start + window - 1] = prev_ema;

            for i in (start + window)..n {
                let current_val = input[i];
                if current_val.is_nan() {
                    out[i] = f64::NAN;
                } else {
                    prev_ema = current_val * alpha + prev_ema * inv_alpha;
                    out[i] = prev_ema;
                }
            }
        }
        _ => out.fill(f64::NAN),
    }
}