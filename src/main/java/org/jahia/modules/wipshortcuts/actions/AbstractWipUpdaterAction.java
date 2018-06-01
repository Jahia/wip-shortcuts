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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class AbstractWipUpdaterAction extends Action {

    private static final Logger logger = LoggerFactory.getLogger(AbstractWipUpdaterAction.class);

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
        if (!isValidRootNode(mainNode)) return ActionResult.BAD_REQUEST;
        toggleWip(mainNode, true, false);
        mainNode.getSession().save();

        return ACTION_RESULT;
    }

    protected abstract boolean isValidRootNode(JCRNodeWrapper node) throws RepositoryException;

    private boolean getWipState(JCRNodeWrapper node) throws RepositoryException {
        final Locale locale = node.getSession().getLocale();
        final Node wipHolder = node.hasI18N(locale) ? node.getI18N(locale) : node.getRealNode();
        return wipHolder.hasProperty(Constants.WORKINPROGRESS) && wipHolder.getProperty(Constants.WORKINPROGRESS).getBoolean();
    }

    private void writeWipState(JCRNodeWrapper node, boolean state) throws RepositoryException {
        final Locale locale = node.getSession().getLocale();
        final Node wipHolder = node.hasI18N(locale) ? node.getI18N(locale) : node.getRealNode();
        wipHolder.setProperty(Constants.WORKINPROGRESS, state);
    }

    private void toggleWip(JCRNodeWrapper node, boolean isRoot, boolean newState) throws RepositoryException {
        final boolean state = isRoot ? !getWipState(node) : newState;
        writeWipState(node, state);
        if (logger.isDebugEnabled()) logger.debug(String.format("WIP=%s on %s", state, node.getPath()));
        for (JCRNodeWrapper child : node.getNodes()) {
            if (canIterate(child)) toggleWip(child, false, state);
        }
    }

    protected abstract boolean canIterate(JCRNodeWrapper child);
}
