package org.jahia.modules.wipshortcuts.actions;

import org.jahia.api.Constants;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WipOnTreeAction extends Action {

    private static final Logger logger = LoggerFactory.getLogger(WipOnTreeAction.class);

    private static final ActionResult ACTION_RESULT;

    static {
        final JSONObject obj = new JSONObject();
        final HashMap<String, String> value = new HashMap<String, String>(1);
        value.put("refreshAll", "true");
        try {
            obj.put("refreshData", value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        ACTION_RESULT = new ActionResult(HttpServletResponse.SC_OK, null, obj);
    }

    @Override
    public ActionResult doExecute(HttpServletRequest req, RenderContext renderContext, Resource resource, JCRSessionWrapper session, Map<String, List<String>> parameters, URLResolver urlResolver) throws Exception {
        final JCRNodeWrapper mainNode = resource.getNode();
        toggleWip(mainNode, true, false);
        mainNode.getSession().save();

        return ACTION_RESULT;
    }

    private void toggleWip(JCRNodeWrapper node, boolean isRoot, boolean newStatus) throws RepositoryException {
        final boolean status = isRoot ?
                !(node.hasProperty(Constants.WORKINPROGRESS) && node.getProperty(Constants.WORKINPROGRESS).getBoolean())
                : newStatus;
        node.setProperty(Constants.WORKINPROGRESS, status);
        if (logger.isDebugEnabled()) logger.debug(String.format("WIP=%s on %s", status, node.getPath()));
        for (JCRNodeWrapper child : node.getNodes()) {
            if (child.isNodeType(Constants.JAHIANT_CONTENT)) toggleWip(child, false, status);
        }
    }
}
