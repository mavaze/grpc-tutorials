package io.grpc.examples.greeting;

import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.greeting.HelloServiceGrpc.HelloServiceBlockingStub;
import io.grpc.examples.greeting.HelloServiceGrpc.HelloServiceFutureStub;
import io.grpc.examples.greeting.HelloServiceGrpc.HelloServiceStub;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GreetingClient {

    private static ClientResponseObserver<HelloRequest, HelloResponse> clientResponseObserver = new ClientResponseObserver<HelloRequest, HelloResponse>() {

        @Override
        public void onNext(HelloResponse value) {
            log.info("ClientResponseObserver onNext(Reply: {}) called", value.getReply());
        }

        @Override
        public void onError(Throwable t) {
            log.error("ClientResponseObserver onError(t) called", t);
        }

        @Override
        public void onCompleted() {
            log.info("ClientResponseObserver onCompleted() called");
        }

        @Override
        public void beforeStart(ClientCallStreamObserver<HelloRequest> requestStream) {
            // log.info("ClientResponseObserver beforeStart(stream) called");
        }
    };

    public static void main(String[] args) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 8980).usePlaintext().build();

        // Async Stub: supports all call types on service
        HelloServiceStub stub = HelloServiceGrpc.newStub(channel);

        // Blocking Stub: unary and streaming output calls on the service
        HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        // Future Stub: supports unary calls on the service
        HelloServiceFutureStub futureStub = HelloServiceGrpc.newFutureStub(channel);

        // unary with blocking stub
        HelloResponse unaryResponse = blockingStub
                .sayHello(HelloRequest.newBuilder().setGreeting("Good morning").build());
        log.info("Unary response received: {}", unaryResponse.getReply());

        // unary with future stub
        ListenableFuture<HelloResponse> futureReply = futureStub
                .sayHello(HelloRequest.newBuilder().setGreeting("").build());

        HelloResponse helloResponse = futureReply.get(3, TimeUnit.SECONDS);
        log.info("Future reply received with get(): {}", helloResponse.getReply());
        
        futureReply.addListener(() -> log.info("Future reply received in listener"), Executors.newSingleThreadExecutor());
        Futures.addCallback(futureReply, new FutureCallback<HelloResponse>() {
            @Override
            public void onSuccess(HelloResponse response) {
                log.info("Future reply received in callback: {}", response.getReply());
            }

            @Override
            public void onFailure(Throwable t) {
            }
        }, Executors.newSingleThreadExecutor());

        // client streaming with async stub
        StreamObserver<HelloRequest> clientGreetings = stub.lotsOfGreetings(clientResponseObserver);
        clientGreetings.onNext(HelloRequest.newBuilder().setGreeting("Client: Good morning").build());
        clientGreetings.onNext(HelloRequest.newBuilder().setGreeting("Client: Good afternoon").build());
        clientGreetings.onNext(HelloRequest.newBuilder().setGreeting("Client: Good night").build());
        clientGreetings.onCompleted();

        // server streaming with blocking stub
        Iterator<HelloResponse> lotsOfReplies = blockingStub
                .lotsOfReplies(HelloRequest.newBuilder().setGreeting("Client once says, good day!!!").build());
        while (lotsOfReplies.hasNext()) {
            log.info("Server streaming response received: {}", lotsOfReplies.next().getReply());
        }
        log.info("Server streaming response completed");

        // bidirectional streaming with async stub
        StreamObserver<HelloRequest> chatRoom = stub.bidiHello(clientResponseObserver);
        chatRoom.onNext(HelloRequest.newBuilder().setGreeting("BidiClient: Good morning").build());
        chatRoom.onNext(HelloRequest.newBuilder().setGreeting("BidiClient: Good afternoon").build());
        chatRoom.onNext(HelloRequest.newBuilder().setGreeting("BidiClient: Good night").build());
        chatRoom.onCompleted();
    }
}
