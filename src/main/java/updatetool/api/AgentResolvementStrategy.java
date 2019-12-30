package updatetool.api;

public interface AgentResolvementStrategy<T> {
    public boolean resolve(T toResolve);
}
