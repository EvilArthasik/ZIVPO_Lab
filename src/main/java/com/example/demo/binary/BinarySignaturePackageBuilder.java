package com.example.demo.binary;

import com.example.demo.dto.MalwareSignatureResponse;
import com.example.demo.signature.DigitalSignatureService;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class BinarySignaturePackageBuilder {
    public static final String DATA_CONTENT_TYPE = "application/vnd.zivpo.signatures+octet-stream";
    public static final String MANIFEST_CONTENT_TYPE = "application/vnd.zivpo.signature-manifest+octet-stream";
    public static final String SIGNATURE_CONTENT_TYPE = "application/vnd.zivpo.signature-manifest-signature";

    private static final byte[] DATA_MAGIC = "ZSGD".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MANIFEST_MAGIC = "ZSGM".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final String SCHEMA = String.join(";",
            "magic:bytes[4]",
            "version:int32",
            "count:int32",
            "record.id:uuid",
            "record.threatName:utf8",
            "record.firstBytes:bytes",
            "record.remainderHash:bytes",
            "record.remainderLength:int64",
            "record.fileType:utf8",
            "record.offsetStart:int64",
            "record.offsetEnd:int64",
            "record.updatedAt:instantMillis",
            "record.status:int8",
            "record.digitalSignature:bytes"
    );

    private final DigitalSignatureService digitalSignatureService;

    public BinarySignaturePackageBuilder(DigitalSignatureService digitalSignatureService) {
        this.digitalSignatureService = digitalSignatureService;
    }

    public BinaryApiPackage build(BinaryApiScope scope, List<MalwareSignatureResponse> signatures) {
        byte[] data = writeData(signatures);
        byte[] manifest = writeManifest(scope, signatures.size(), data);
        byte[] signature = Base64.getDecoder().decode(digitalSignatureService.signManifest(manifest));
        return new BinaryApiPackage(manifest, signature, data);
    }

    private byte[] writeData(List<MalwareSignatureResponse> signatures) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(buffer);
            output.write(DATA_MAGIC);
            output.writeInt(VERSION);
            output.writeInt(signatures.size());
            for (MalwareSignatureResponse signature : signatures) {
                writeUuid(output, signature.id());
                writeUtf8(output, signature.threatName());
                writeBytes(output, hexBytes(signature.firstBytesHex()));
                writeBytes(output, hexBytes(signature.remainderHashHex()));
                output.writeLong(signature.remainderLength());
                writeUtf8(output, signature.fileType());
                output.writeLong(signature.offsetStart());
                output.writeLong(signature.offsetEnd());
                output.writeLong(signature.updatedAt().toEpochMilli());
                output.writeByte(statusCode(signature.status().name()));
                writeBytes(output, Base64.getDecoder().decode(signature.digitalSignatureBase64()));
            }
            output.flush();
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to build binary signatures response", exception);
        }
    }

    private byte[] writeManifest(BinaryApiScope scope, int count, byte[] data) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(buffer);
            output.write(MANIFEST_MAGIC);
            output.writeInt(VERSION);
            writeUtf8(output, scope.name());
            output.writeLong(Instant.now().toEpochMilli());
            output.writeInt(count);
            writeUtf8(output, "signatures.bin");
            writeUtf8(output, DATA_CONTENT_TYPE);
            output.writeLong(data.length);
            writeBytes(output, sha256(data));
            writeUtf8(output, "SHA-256");
            writeUtf8(output, digitalSignatureService.algorithm());
            writeSchema(output);
            output.flush();
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to build binary signatures manifest", exception);
        }
    }

    private void writeSchema(DataOutputStream output) throws IOException {
        output.writeInt(14);
        writeSchemaField(output, "magic", BinaryFieldType.BYTES);
        writeSchemaField(output, "version", BinaryFieldType.INT32);
        writeSchemaField(output, "count", BinaryFieldType.INT32);
        writeSchemaField(output, "id", BinaryFieldType.UUID);
        writeSchemaField(output, "threatName", BinaryFieldType.UTF8);
        writeSchemaField(output, "firstBytes", BinaryFieldType.BYTES);
        writeSchemaField(output, "remainderHash", BinaryFieldType.BYTES);
        writeSchemaField(output, "remainderLength", BinaryFieldType.INT64);
        writeSchemaField(output, "fileType", BinaryFieldType.UTF8);
        writeSchemaField(output, "offsetStart", BinaryFieldType.INT64);
        writeSchemaField(output, "offsetEnd", BinaryFieldType.INT64);
        writeSchemaField(output, "updatedAt", BinaryFieldType.INSTANT_MILLIS);
        writeSchemaField(output, "status", BinaryFieldType.INT8);
        writeSchemaField(output, "digitalSignature", BinaryFieldType.BYTES);
        writeUtf8(output, SCHEMA);
    }

    private void writeSchemaField(DataOutputStream output, String name, BinaryFieldType type) throws IOException {
        writeUtf8(output, name);
        output.writeByte(type.code());
    }

    private void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private void writeUtf8(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private byte[] hexBytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate binary response hash", exception);
        }
    }

    private int statusCode(String status) {
        return switch (status) {
            case "ACTUAL" -> 1;
            case "DELETED" -> 2;
            default -> 0;
        };
    }
}
