package org.jahia.modules.wipshortcuts.actions;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import org.jahia.api.Constants;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.services.content.*;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.utils.security.AccessManagerUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.security.Privilege;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

public abstract class AbstractWipUpdaterAction extends Action {

    private static final Logger logger = LoggerFactory.getLogger(AbstractWipUpdaterAction.class);

    private static final ActionResult ACTION_RESULT;

    private Set<String> languages;

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
        toggleWip(mainNode);
        mainNode.getSession().save();

        return ACTION_RESULT;
    }

    protected abstract boolean isValidRootNode(JCRNodeWrapper node) throws RepositoryException;

    private String getWipState(JCRNodeWrapper node) throws RepositoryException {
        final Locale locale = node.getSession().getLocale();
        final Node wipHolder = node.getRealNode();
        languages = existingWipLang(wipHolder);

        if(wipHolder.hasProperty(Constants.WORKINPROGRESS_STATUS) &&
                Constants.WORKINPROGRESS_STATUS_LANG.equals(wipHolder.getProperty(Constants.WORKINPROGRESS_STATUS).getString())
         ){
            if(wipHolder.hasProperty(Constants.WORKINPROGRESS_LANGUAGES)){

                if( languages.contains(locale.toString()) && languages.size()==1){
                    return Constants.WORKINPROGRESS_STATUS_DISABLED;
                }else{
                    return Constants.WORKINPROGRESS_STATUS_LANG;
                }
            }
        }else if (wipHolder.hasProperty(Constants.WORKINPROGRESS_STATUS) && Constants.WORKINPROGRESS_STATUS_ALLCONTENT.equals(wipHolder.getProperty(Constants.WORKINPROGRESS_STATUS).getString())) {
            if( languages.contains(locale.toString()) && languages.size()==1){
                return Constants.WORKINPROGRESS_STATUS_DISABLED;
            }else{
                return Constants.WORKINPROGRESS_STATUS_LANG;
            }
        }else {
            return Constants.WORKINPROGRESS_STATUS_LANG;
        }
        return null;

    }

     private void toggleWip(JCRNodeWrapper node) throws RepositoryException {
        final String state = getWipState(node);
        saveWipPropertiesIfNeeded(node,state);
        if (logger.isDebugEnabled()) logger.debug(String.format("WIP=%s on %s", state, node.getPath()));
        for (JCRNodeWrapper child : node.getNodes()) {
            if (canIterate(child)) toggleWip(child);
        }
    }

    protected abstract boolean canIterate(JCRNodeWrapper child);

    private Set<String> existingWipLang(Node node){
        Set<String> existingWipLanguages = Collections.emptySet();
        try {
            if (node.hasProperty(Constants.WORKINPROGRESS_LANGUAGES)) {
                existingWipLanguages = new HashSet<>();
                for (Value lang : node.getProperty(Constants.WORKINPROGRESS_LANGUAGES).getValues()) {
                    existingWipLanguages.add(lang.getString());
                }
            }
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
        return existingWipLanguages;
    }

    private void updateWipStatus(JCRNodeWrapper node, final String wipStatusToSet, final Set<String> wipLangugagesToSet)
            throws RepositoryException {
        if (wipStatusToSet == null && wipLangugagesToSet == null) {
            return;
        }
        JCRSessionWrapper session = node.getSession();
        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(session.getUser(), session.getWorkspace().getName(),
                session.getLocale(), new JCRCallback<Void>() {
                    @Override
                    public Void doInJCR(JCRSessionWrapper systemSession) throws RepositoryException {
                        Node targetNode = systemSession.getProviderSession(node.getProvider())
                                .getNodeByIdentifier(node.getIdentifier());
                        boolean debugEnabled = logger.isDebugEnabled();
                        String effectiveWipStatusToSet = wipStatusToSet;
                        if (effectiveWipStatusToSet != null) {
                            targetNode.setProperty(Constants.WORKINPROGRESS_STATUS, effectiveWipStatusToSet);
                            if (debugEnabled) {
                                logger.debug("Setting WIP status on node {} to {}", targetNode.getPath(),
                                        effectiveWipStatusToSet);
                            }
                        } else if (wipLangugagesToSet != null && wipLangugagesToSet.isEmpty()) {
                            // languages are empty
                            if (targetNode.hasProperty(Constants.WORKINPROGRESS_STATUS) && Constants.WORKINPROGRESS_STATUS_LANG.equals(targetNode.getProperty(Constants.WORKINPROGRESS_STATUS).getString())) {
                                // in this case we are removing WIP completely
                                effectiveWipStatusToSet = Constants.WORKINPROGRESS_STATUS_DISABLED;
                            }
                        }

                        if (effectiveWipStatusToSet != null && (Constants.WORKINPROGRESS_STATUS_DISABLED.equals(effectiveWipStatusToSet)
                                || wipLangugagesToSet != null && wipLangugagesToSet.isEmpty())) {
                            targetNode.setProperty(Constants.WORKINPROGRESS_LANGUAGES, (Value[]) null);
                            targetNode.setProperty(Constants.WORKINPROGRESS_STATUS, (Value) null);
                            if (debugEnabled) {
                                logger.debug("Removing WIP status property on node {}", targetNode.getPath());
                            }
                        } else if (wipLangugagesToSet != null) {
                            targetNode.setProperty(Constants.WORKINPROGRESS_LANGUAGES,
                                    JCRContentUtils.createValues(wipLangugagesToSet, systemSession.getValueFactory()));
                            if (debugEnabled) {
                                logger.debug("Setting WIP languages on node {} to {}", targetNode.getPath(),
                                        wipLangugagesToSet);
                            }
                        }
                        targetNode.getSession().save();
                        return null;
                    }
                });
    }

    private void saveWipPropertiesIfNeeded(JCRNodeWrapper node,String newWipStatus)
            throws RepositoryException {

        final Locale locale = node.getSession().getLocale();
        Set<String> newWipLanguages = new HashSet<String>();
        JCRSessionWrapper session = node.getSession();



        if ((Constants.WORKINPROGRESS_STATUS_ALLCONTENT.equals(newWipStatus)
                || Constants.WORKINPROGRESS_STATUS_DISABLED.equals(newWipStatus))
                && !node.hasPermission(AccessManagerUtils.getPrivilegeName(Privilege.JCR_MODIFY_PROPERTIES,
                session.getWorkspace().getName()))) {
            // we do not allow translators to change WIP status type to all content or disabled
            newWipStatus = null;
        }
        if(!languages.isEmpty()){
            newWipLanguages = languages;
            if(languages.contains(locale.toString())){
                newWipLanguages.remove(locale.toString());
            }else{
                newWipLanguages.add(locale.toString());
            }
        }else{
            newWipLanguages.add(locale.toString());
        }


        if (!newWipLanguages.isEmpty()) {
            // we do have changes
            if (!node.hasPermission(AccessManagerUtils.getPrivilegeName(Privilege.JCR_MODIFY_PROPERTIES,
                    session.getWorkspace().getName()))) {
                for (String modifiedLang : newWipLanguages) {
                    if (!node.hasPermission(AccessManagerUtils.getPrivilegeName(Privilege.JCR_MODIFY_PROPERTIES,
                            session.getWorkspace().getName()) + "_" + modifiedLang)) {
                        throw new AccessDeniedException("Unable to update Work In Progress information on node "
                                + node.getPath() + " for user " + session.getUser().getName() + " in locale "
                                + modifiedLang);
                    }
                }
            }
        } else if( languages.size() >= 0){
           newWipStatus = Constants.WORKINPROGRESS_STATUS_DISABLED;
           newWipLanguages = new HashSet<String>();
        } else {
            // no changes so far
            newWipLanguages = null;
        }

        updateWipStatus(node, newWipStatus, newWipLanguages);

    }
}
