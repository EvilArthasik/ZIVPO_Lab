package com.example.demo.binary;

public record BinaryApiPackage(
        byte[] manifest,
        byte[] manifestSignature,
        byte[] data
) {
}
