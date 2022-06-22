package io.grpc.examples.greeting;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.Iterator;

import com.google.common.collect.Iterators;

@Slf4j
public class GreetingsServer {

    private final int port;
    private final Server server;

    public GreetingsServer(int port) throws IOException {
        this.port = port;
        server = ServerBuilder.forPort(port).addService(new GreetingsService()).build();
    }

    public void start() throws IOException {
        server.start();
        log.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown
            // hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            try {
                GreetingsServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    @Slf4j
    private static class GreetingsService extends HelloServiceGrpc.HelloServiceImplBase {

        @Override
        // unary
        // A simple RPC where the client sends a request to the server using the stub
        // and waits for a response to come back, just like a normal function call.
        public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
            responseObserver.onNext(HelloResponse.newBuilder().setReply("Same to you!!!").build()); // Receives a value from the stream. Unary calls must invoke only once.
            responseObserver.onCompleted();
        }

        @Override
        // server streaming
        // A server-side streaming RPC where the client sends a request to the server and gets a stream to read
        // a sequence of messages back. The client reads from the returned stream until there are no more messages.
        public void lotsOfReplies(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
            Arrays.asList("Morning, enjoy your breakfast!", "Afternoon, enjoy your meal!", "Night, sweet dreams!")
                    .stream()
                    .map(reply -> HelloResponse.newBuilder().setReply(reply).build())
                    .forEach(response -> responseObserver.onNext(response));
            responseObserver.onCompleted();
        }

        @Override
        // client streaming
        // A client-side streaming RPC where the client writes a sequence of messages and sends them to the server.
        // Once the client has finished writing the messages, it waits for the server to read them all and return its response.
        public StreamObserver<HelloRequest> lotsOfGreetings(StreamObserver<HelloResponse> responseObserver) {
            return new StreamObserver<HelloRequest>() {

                @Override
                // Processes stream of requests from client, this will be called multiple times
                public void onNext(HelloRequest request) {
                    log.info("Greetings received from client: {}", request);
                }

                @Override
                public void onError(Throwable t) {
                    log.error("Encountered error in processing client streamed greetings", t);
                }

                @Override
                public void onCompleted() {
                    // Server can invoke responseObserver.onNext() only once in client streaming.
                    responseObserver.onNext(
                            HelloResponse.newBuilder().setReply("Thanks for your bunch of greetings").build()); // Server sends response when the client finished sending messages
                    responseObserver.onCompleted(); // successful completion of stream. Can be called only once and then onNext is NOT allowed post that.
                }
            };
        }

        @Override
        // bidirectional
        // A bidirectional streaming RPC where both sides send a sequence of messages using a read-write stream.
        // The two streams operate independently, so clients and servers can read and write in whatever order they like:
        // for example, the server could wait to receive all the client messages before writing its responses, or
        // it could alternately read a message then write a message, or some other combination of reads and writes.
        // The order of messages in each stream is preserved.
        public StreamObserver<HelloRequest> bidiHello(StreamObserver<HelloResponse> responseObserver) {
            return new StreamObserver<HelloRequest>() {
                private Iterator<String> respIterator = Iterators.forArray(
                        "BidiServer: Good morning!!!",
                        "BidiServer: Good afternoon!!!");

                @Override
                public void onNext(HelloRequest request) {
                    if (respIterator.hasNext()) {
                        responseObserver.onNext(HelloResponse.newBuilder().setReply(respIterator.next()).build());
                    }
                }

                @Override
                public void onError(Throwable t) {

                }

                @Override
                public void onCompleted() {
                    // when this method is executed, meaning client has stopped sending messages
                    // though server may continue sending messages before marking completed
                    responseObserver
                            .onNext(HelloResponse.newBuilder().setReply("BidiServer: Good night!!!").build());
                    responseObserver.onCompleted();
                }
            };
        }
    }

    public static void main(String[] args) throws Exception {
        GreetingsServer server = new GreetingsServer(8980);
        server.start();
        server.blockUntilShutdown();
    }
}
