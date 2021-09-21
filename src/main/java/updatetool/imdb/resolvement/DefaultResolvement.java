package updatetool.imdb.resolvement;

import org.tinylog.Logger;
import updatetool.api.AgentResolvementStrategy;
import updatetool.common.Capabilities;
import updatetool.imdb.ImdbDockerImplementation;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class DefaultResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {

    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        if(!ImdbDockerImplementation.checkCapability(Capabilities.IGNORE_NO_MATCHING_RESOLVER_LOG)) {
            Logger.warn("Item: '{}' has no matching IMDB resolver and will be ignored. (guid={})", toResolve.title, toResolve.guid);
        }
        return false;
    }

}
