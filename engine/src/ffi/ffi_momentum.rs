use crate::indicators::moving_average::{calc_sma, calc_ema};
use crate::indicators::momentum::{calc_roc, calc_macd, calc_stochastic};

#[unsafe(no_mangle)]
pub unsafe extern "C" fn compute_sma(
    prices_ptr: *const f64,
    len: usize,
    window: usize,
    out_ptr: *mut f64,
) {
    let prices = unsafe { std::slice::from_raw_parts(prices_ptr, len) };
    let out = unsafe { std::slice::from_raw_parts_mut(out_ptr, len) };
    calc_sma(prices, window, out);
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn compute_ema(
    prices_ptr: *const f64,
    len: usize,
    window: usize,
    out_ptr: *mut f64,
) {
    let prices = unsafe { std::slice::from_raw_parts(prices_ptr, len) };
    let out = unsafe { std::slice::from_raw_parts_mut(out_ptr, len) };
    calc_ema(prices, window, out);
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn compute_roc(
    prices_ptr: *const f64,
    len: usize,
    period: usize,
    out_ptr: *mut f64,
) {
    let prices = unsafe { std::slice::from_raw_parts(prices_ptr, len) };
    let out = unsafe { std::slice::from_raw_parts_mut(out_ptr, len) };
    calc_roc(prices, period, out);
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn compute_macd(
    prices_ptr: *const f64,
    len: usize,
    fast: usize,
    slow: usize,
    signal: usize,
    out_macd_ptr: *mut f64,
    out_signal_ptr: *mut f64,
    out_hist_ptr: *mut f64,
) {
    let prices = unsafe { std::slice::from_raw_parts(prices_ptr, len) };
    let out_macd = unsafe { std::slice::from_raw_parts_mut(out_macd_ptr, len) };
    let out_signal = unsafe { std::slice::from_raw_parts_mut(out_signal_ptr, len) };
    let out_hist = unsafe { std::slice::from_raw_parts_mut(out_hist_ptr, len) };
    calc_macd(prices, fast, slow, signal, out_macd, out_signal, out_hist);
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn compute_stochastic(
    high_ptr: *const f64,
    low_ptr: *const f64,
    close_ptr: *const f64,
    len: usize,
    lookback: usize,
    k_smoothing: usize,
    d_smoothing: usize,
    out_k_ptr: *mut f64,
    out_d_ptr: *mut f64,
) {
    let high = unsafe { std::slice::from_raw_parts(high_ptr, len) };
    let low = unsafe { std::slice::from_raw_parts(low_ptr, len) };
    let close = unsafe { std::slice::from_raw_parts(close_ptr, len) };
    let out_k = unsafe { std::slice::from_raw_parts_mut(out_k_ptr, len) };
    let out_d = unsafe { std::slice::from_raw_parts_mut(out_d_ptr, len) };
    calc_stochastic(high, low, close, lookback, k_smoothing, d_smoothing, out_k, out_d);
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn compute_rsi(
    prices_ptr: *const f64,
    len: usize,
    period: usize,
    out_ptr: *mut f64,
) {
    let prices = unsafe { std::slice::from_raw_parts(prices_ptr, len) };
    let out = unsafe { std::slice::from_raw_parts_mut(out_ptr, len) };
    calc_rsi(prices, period, out);
}