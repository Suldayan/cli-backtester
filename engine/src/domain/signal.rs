#[repr(C)]
pub struct Signal {
    pub action: i32,
    pub symbol_id: i32,
    pub timestamp: i64,
    pub price: f64,
    pub indicator: f64,
}