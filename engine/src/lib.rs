#[repr(C)] 
#[derive(Debug, Clone, Copy)]
pub struct Candle {
    pub timestamp: i64, 
    pub open: f64,      
    pub high: f64,      
    pub low: f64,       
    pub close: f64,     
    pub volume: f64,    
} 

// Test function for the connection between rust and java
#[unsafe(no_mangle)]
pub unsafe extern "C" fn process_candle_batch(ptr: *const Candle, len: usize) -> f64 {
    #[allow(unsafe_op_in_unsafe_fn)]
    let candles = std::slice::from_raw_parts(ptr, len);
    
    let sum: f64 = candles.iter().map(|c| c.close).sum();
    sum / len as f64
}