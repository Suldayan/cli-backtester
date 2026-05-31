package com.example.result;

public enum Ansi {
    RESET("\u001B[0m"),
    RED("\u001B[31m"),
    GREEN("\u001B[32m"),
    YELLOW("\u001B[33m"),
    BLUE("\u001B[34m"),
    CYAN("\u001B[36m");

    public final String code;

    Ansi(String code) {
        this.code = code;
    }

    public String wrap(String text) {
        return code + text + RESET.code;
    }
}

