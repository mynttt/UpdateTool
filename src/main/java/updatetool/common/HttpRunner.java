package updatetool.common;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.tinylog.Logger;

public class HttpRunner<B, R, P> {
    private final int maxTries;
    private final Converter<B, R> converter;
    private final HttpCodeHandler<B, R, P> codeHandler;
    private final String identifier;
    
    public static class RunnerResult<T> {
        public final boolean success;
        private final T result;
        
        private RunnerResult(boolean success, T result) {
            this.success = success;
            this.result = result;
        }
        
        public Optional<T> result() {
            return Optional.ofNullable(result);
        }
        
        public static <T> RunnerResult<T> ofSuccess(T result) { 
            return new RunnerResult<>(true, result);
        }
        
        public static <T> RunnerResult<T> ofFailure(T result) { 
            return new RunnerResult<>(false, result);
        }
    }
    
    public static class HttpCodeHandler<B, R, P> {
        private final Map<Integer, Handler<B, R, P>> handlers = new HashMap<>();
        private final Handler<B, R, P> defaultHandler;
        private String identifier;
        
        private HttpCodeHandler(Map<Integer, Handler<B, R, P>> handlers, Handler<B, R, P> defaultHandler) {
            if(handlers == null || handlers.isEmpty()) {
                this.handlers.put(200, (body, result, payload) -> RunnerResult.ofSuccess(result));
            } else {
                this.handlers.putAll(handlers);
            }
            this.defaultHandler = defaultHandler == null 
                    ? (body, result, payload) 
                        -> {
                            Logger.error("{} : Unhandled HTTP Error [response={} | payload={}]", identifier, body, body.body());
                            return RunnerResult.ofFailure(result);
                        }
                    : defaultHandler;
        }
        
        public static <B, R, P> HttpCodeHandler<B, R, P> of(Map<Integer, Handler<B, R, P>> handlers) {
            Objects.requireNonNull(handlers);
            return new HttpCodeHandler<>(handlers, null);
        }
        
        public static <B, R, P> HttpCodeHandler<B, R, P> of(Map<Integer, Handler<B, R, P>> handlers, Handler<B, R, P> defaultHandler) {
            Objects.requireNonNull(handlers);
            Objects.requireNonNull(defaultHandler);
            return new HttpCodeHandler<>(handlers, defaultHandler);
        }
        
        public static <B, R, P> HttpCodeHandler<B, R, P> ofDefault(Handler<B, R, P> defaultHandler) {
            Objects.requireNonNull(defaultHandler);
            return new HttpCodeHandler<>(null, defaultHandler);
        }
        
        RunnerResult<R> handle(Converter<B, R> converter, HttpResponse<B> response, P payload) {
            return handlers.getOrDefault(response.statusCode(), defaultHandler).handle(response, converter.convert(response), payload);
        }
    }
    
    @FunctionalInterface
    public interface Converter<B, R> {
        public R convert(HttpResponse<B> toConvert);
    }
    
    @FunctionalInterface
    public interface Handler<B, R, P> {
        public RunnerResult<R> handle(HttpResponse<B> response, R result, P payload);
    }
    
    @FunctionalInterface
    public interface ResponseSupplier<B> {
        public HttpResponse<B> supply() throws Exception;
    }
    
    public HttpRunner(Converter<B, R> converter,
            HttpCodeHandler<B, R, P> codeHandler,
            String identifier,
            int maxTries) {
        if(maxTries <= 0)
            throw new IllegalArgumentException("maxTries <= 0");
        Objects.requireNonNull(converter);
        Objects.requireNonNull(codeHandler);
        Objects.requireNonNull(identifier);
        this.converter = converter;
        this.codeHandler = codeHandler;
        this.identifier = identifier;
        this.codeHandler.identifier = identifier;
        this.maxTries = 3;
    }
    
    public RunnerResult<R> run(ResponseSupplier<B> responseFactory, P payload) {
        HttpResponse<B> response = null;
        Exception ex = null;
        
        for(int i = 0; i < maxTries; i++) {
            try {
                response = responseFactory.supply();
                var res = codeHandler.handle(converter, response, payload);
                
                if(res.success)
                    return res;
                
            } catch(Exception e) {
                ex = e;
                Logger.warn("{} : HTTP request failed or pipeline processing error ({}). [{}/{}] => {}",
                        identifier, e.getClass().getSimpleName(), i+1, maxTries, e.getMessage());
                Logger.warn("{} : Dumping HTTP response => {} | Payload: {}", response, response.body());
            }
        }
        
        if(ex != null)
            throw Utility.rethrow(ex);
        
        Logger.error("{} : Failed to resolve in {} tries. No custom handlers specified.", identifier, maxTries);
        return RunnerResult.ofFailure(response == null ? null : converter.convert(response));
    }
    
}
