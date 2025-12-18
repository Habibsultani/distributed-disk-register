package com.example.family;

import family.MessageId;
import family.StoredMessage;
import family.StoreResult;
import family.StorageServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class StorageServiceImpl extends StorageServiceGrpc.StorageServiceImplBase {

    private final Map<String, String> store;

    public StorageServiceImpl(Map<String, String> store) {
        this.store = store;
    }

    @Override
    public void store(StoredMessage request, StreamObserver<StoreResult> responseObserver) {
        String id = Integer.toString(request.getId());
        String text = request.getText();

        try {
            Path messagesDir = Path.of("messages");
            Files.createDirectories(messagesDir);

            Path file = messagesDir.resolve(id + ".msg");
            Files.writeString(
                    file,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            store.put(id, text);
            responseObserver.onNext(StoreResult.newBuilder().setOk(true).build());
            responseObserver.onCompleted();
        } catch (IOException e) {
            responseObserver.onNext(StoreResult.newBuilder()
                    .setOk(false)
                    .setError(e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void retrieve(MessageId request, StreamObserver<StoredMessage> responseObserver) {
        String id = Integer.toString(request.getId());
        String text = store.get(id);

        if (text != null) {
            responseObserver.onNext(StoredMessage.newBuilder()
                    .setId(request.getId())
                    .setText(text)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        Path file = Path.of("messages", id + ".msg");
        if (!Files.exists(file)) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Message not found").asRuntimeException());
            return;
        }

        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
            store.put(id, text);

            responseObserver.onNext(StoredMessage.newBuilder()
                    .setId(request.getId())
                    .setText(text)
                    .build());
            responseObserver.onCompleted();
        } catch (IOException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
