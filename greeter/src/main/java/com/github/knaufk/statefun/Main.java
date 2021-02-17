package com.github.knaufk.statefun;

import static io.undertow.UndertowOptions.ENABLE_HTTP2;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.apache.flink.statefun.sdk.java.Context;
import org.apache.flink.statefun.sdk.java.StatefulFunction;
import org.apache.flink.statefun.sdk.java.StatefulFunctionSpec;
import org.apache.flink.statefun.sdk.java.StatefulFunctions;
import org.apache.flink.statefun.sdk.java.TypeName;
import org.apache.flink.statefun.sdk.java.ValueSpec;
import org.apache.flink.statefun.sdk.java.handler.RequestReplyHandler;
import org.apache.flink.statefun.sdk.java.io.KinesisEgressMessage;
import org.apache.flink.statefun.sdk.java.message.Message;
import org.apache.flink.statefun.sdk.java.slice.Slice;
import org.apache.flink.statefun.sdk.java.slice.Slices;

public class Main {

  private static final class GreeterFn implements StatefulFunction {

    static final TypeName TYPE = TypeName.typeNameFromString("com.knaufk.fns/greeter");

    static final ValueSpec<Integer> SEEN =
        ValueSpec.named("seen").thatExpireAfterWrite(Duration.ofDays(7)).withIntType();

    static final TypeName GREETS_EGRESS = TypeName.typeNameFromString("com.knaufk/greets");

    @Override
    public CompletableFuture<Void> apply(Context context, Message message) {
      return CompletableFuture.runAsync(
          () -> {
            var storage = context.storage();
            var seen = storage.get(SEEN).orElse(0);

            if (!message.is(MyCustomTypes.USER_TYPE)) {
              throw new IllegalStateException("Not a user type?!");
            }
            User user = message.as(MyCustomTypes.USER_TYPE);
            String name = user.getName();

            context.send(
                KinesisEgressMessage.forEgress(GREETS_EGRESS)
                    .withStream("greetings")
                    .withUtf8PartitionKey(name)
                    .withUtf8Value("Hello " + name + ", for the " + seen + "th time!")
                    .build());

            storage.set(SEEN, seen + 1);
          });
    }
  }

  public static void main(String... args) {
    // defines the greeter spec.
    StatefulFunctionSpec spec =
        StatefulFunctionSpec.builder(GreeterFn.TYPE)
            .withValueSpec(GreeterFn.SEEN)
            .withSupplier(GreeterFn::new)
            .build();

    // obtain a request-reply handler based on the spec above
    StatefulFunctions functions = new StatefulFunctions();
    functions.withStatefulFunction(spec);
    RequestReplyHandler handler = functions.requestReplyHandler();

    // this is a generic HTTP server that hands off the request-body
    // to the handler above and visa versa.
    Undertow server =
        Undertow.builder()
            .addHttpListener(5000, "0.0.0.0")
            .setHandler(new StateFunUndertow(handler))
            .setServerOption(ENABLE_HTTP2, true)
            .build();
    server.start();
  }

  private static final class StateFunUndertow implements HttpHandler {
    private final RequestReplyHandler handler;

    StateFunUndertow(RequestReplyHandler handler) {
      this.handler = Objects.requireNonNull(handler);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      exchange.getRequestReceiver().receiveFullBytes(this::onRequestBody);
    }

    @SuppressWarnings("CatchMayIgnoreException")
    private void onRequestBody(HttpServerExchange exchange, byte[] requestBytes) {
      try {
        CompletableFuture<Slice> future = handler.handle(Slices.wrap(requestBytes));
        exchange.dispatch();
        future.whenComplete(
            (responseBytes, ex) -> {
              if (ex != null) {
                onException(exchange, ex);
              } else {
                onSuccess(exchange, responseBytes);
              }
            });
      } catch (Throwable t) {
        onException(exchange, t);
      }
    }

    private void onException(HttpServerExchange exchange, Throwable t) {
      t.printStackTrace(System.out);
      exchange.getResponseHeaders().put(Headers.STATUS, 500);
      exchange.endExchange();
    }

    private void onSuccess(HttpServerExchange exchange, Slice result) {
      exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
      exchange.getResponseSender().send(result.asReadOnlyByteBuffer());
    }
  }
}
