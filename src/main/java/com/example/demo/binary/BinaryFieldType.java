package com.example.demo.binary;

public enum BinaryFieldType {
    INT8(1),
    INT32(2),
    INT64(3),
    UTF8(4),
    BYTES(5),
    UUID(6),
    INSTANT_MILLIS(7);

    private final byte code;

    BinaryFieldType(int code) {
        this.code = (byte) code;
    }

    public byte code() {
        return code;
    }
}
