package org.jahia.modules.wipshortcuts.actions;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.utils.LanguageCodeConverters;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jahia.api.Constants.WORKINPROGRESS_LANGUAGES;
import static org.jahia.api.Constants.WORKINPROGRESS_STATUS;
import static org.jahia.api.Constants.WORKINPROGRESS_STATUS_ALLCONTENT;
import static org.jahia.api.Constants.WORKINPROGRESS_STATUS_DISABLED;
import static org.jahia.api.Constants.WORKINPROGRESS_STATUS_LANG;

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

    protected abstract boolean isValidRootNode(JCRNodeWrapper node);

    protected abstract boolean canIterate(JCRNodeWrapper child);

    @Override
    public ActionResult doExecute(HttpServletRequest req, RenderContext renderContext, Resource resource, JCRSessionWrapper session, Map<String, List<String>> parameters, URLResolver urlResolver) throws Exception {
        final JCRNodeWrapper mainNode = resource.getNode();
        if (!isValidRootNode(mainNode)) return ActionResult.BAD_REQUEST;
        toggleWip(mainNode, !getWipStatus(mainNode));

        return ACTION_RESULT;
    }

    private boolean getWipStatus(JCRNodeWrapper node) throws RepositoryException {
        if (!node.hasProperty(WORKINPROGRESS_STATUS)) return false;
        switch (node.getPropertyAsString(WORKINPROGRESS_STATUS)) {
            case WORKINPROGRESS_STATUS_DISABLED:
                return false;
            case WORKINPROGRESS_STATUS_ALLCONTENT:
                return true;
            case WORKINPROGRESS_STATUS_LANG:
                if (!node.hasProperty(WORKINPROGRESS_LANGUAGES)) return false;

                final String currentLocale = getSessionLocale(node);
                for (Value lang : node.getProperty(WORKINPROGRESS_LANGUAGES).getValues()) {
                    if (StringUtils.equals(currentLocale, lang.getString())) return true;
                }
        }
        return false;
    }

    private void toggleWip(JCRNodeWrapper node, boolean newStatus) throws RepositoryException {
        updateWipStatus(node, newStatus);
        if (logger.isDebugEnabled()) logger.debug(String.format("WIP=%s on %s", newStatus, node.getPath()));
        for (JCRNodeWrapper child : node.getNodes()) {
            if (canIterate(child)) toggleWip(child, newStatus);
        }
    }

    private void updateWipStatus(JCRNodeWrapper node, boolean status) throws RepositoryException {
        final String currentLocale = getSessionLocale(node);
        final JCRSessionWrapper session = node.getSession();
        if (!node.hasPermission(AccessManagerUtils.getPrivilegeName(Privilege.JCR_MODIFY_PROPERTIES, session.getWorkspace().getName())) &&
                !node.hasPermission(String.format("%s_%s", AccessManagerUtils.getPrivilegeName(Privilege.JCR_MODIFY_PROPERTIES, session.getWorkspace().getName()), currentLocale))) {
            throw new AccessDeniedException(String.format("Unable to update Work In Progress information on node %s for user %s in locale %s",
                    node.getPath(), session.getUser().getName(), currentLocale));
        }

        if (status) {
            if (!node.hasProperty(WORKINPROGRESS_STATUS)) {
                writeWipStatus(node, currentLocale);
                return;
            }
            switch (node.getPropertyAsString(WORKINPROGRESS_STATUS)) {
                case WORKINPROGRESS_STATUS_DISABLED:
                    writeWipStatus(node, currentLocale);
                    break;
                case WORKINPROGRESS_STATUS_ALLCONTENT:
                    break;
                case WORKINPROGRESS_STATUS_LANG:
                    final Set<String> languages;
                    boolean updated = true;
                    if (!node.hasProperty(WORKINPROGRESS_LANGUAGES)) {
                        languages = Collections.singleton(currentLocale);
                    } else {
                        languages = getFlaggedLangs(node);
                        updated = languages.add(currentLocale);
                    }
                    if (updated)
                        writeWipStatus(node, languages);
            }
        } else {
            if (!node.hasProperty(WORKINPROGRESS_STATUS)) return;
            Set<String> languages = null;
            switch (node.getPropertyAsString(WORKINPROGRESS_STATUS)) {
                case WORKINPROGRESS_STATUS_DISABLED:
                    break;
                case WORKINPROGRESS_STATUS_ALLCONTENT:
                    languages = getSiteLanguages(node);
                    languages.remove(currentLocale);
                    writeWipStatus(node, languages);
                    break;
                case WORKINPROGRESS_STATUS_LANG:
                    boolean updated = true;
                    if (node.hasProperty(WORKINPROGRESS_LANGUAGES)) {
                        languages = getFlaggedLangs(node);
                        updated = languages.remove(currentLocale);
                    }
                    if (updated)
                        writeWipStatus(node, languages);
            }
        }
    }

    private Set<String> getFlaggedLangs(JCRNodeWrapper node) throws RepositoryException {
        final Set<String> languages = new HashSet<>();
        final JCRValueWrapper[] values = node.getProperty(WORKINPROGRESS_LANGUAGES).getValues();
        for (Value val : values) {
            final String lang = val.getString();
            languages.add(lang);
        }
        return languages;
    }

    private void writeWipStatus(JCRNodeWrapper node, final String localeToSet) throws RepositoryException {
        writeWipStatus(node, Collections.singleton(localeToSet));
    }

    private void writeWipStatus(JCRNodeWrapper node, final Set<String> wipLanguagesToSet) throws RepositoryException {
        final boolean disableWip = CollectionUtils.isEmpty(wipLanguagesToSet);

        if (disableWip &&
                (!node.hasProperty(WORKINPROGRESS_STATUS) || WORKINPROGRESS_STATUS_DISABLED.equals(node.getPropertyAsString(WORKINPROGRESS_STATUS))))
            return;

        final JCRSessionWrapper session = node.getSession();
        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(session.getUser(), session.getWorkspace().getName(),
                session.getLocale(), new JCRCallback<Void>() {
                    @Override
                    public Void doInJCR(JCRSessionWrapper systemSession) throws RepositoryException {
                        final Node targetNode = systemSession.getProviderSession(node.getProvider()).getNodeByIdentifier(node.getIdentifier());
                        final boolean debugEnabled = logger.isDebugEnabled();
                        boolean needSave = true;
                        final String wipStatus;
                        if (disableWip) {
                            wipStatus = WORKINPROGRESS_STATUS_DISABLED;
                        } else {
                            final Set<String> siteLanguages = getSiteLanguages(node);
                            if (wipLanguagesToSet.containsAll(siteLanguages)) {
                                wipStatus = WORKINPROGRESS_STATUS_ALLCONTENT;
                            } else if (targetNode.hasProperty(WORKINPROGRESS_LANGUAGES)) {
                                final Set<String> updatedLanguagesList = new HashSet<>(wipLanguagesToSet);
                                for (Value value : targetNode.getProperty(WORKINPROGRESS_LANGUAGES).getValues()) {
                                    updatedLanguagesList.add(value.getString());
                                }
                                if (updatedLanguagesList.containsAll(siteLanguages)) {
                                    wipStatus = WORKINPROGRESS_STATUS_ALLCONTENT;
                                } else {
                                    wipStatus = WORKINPROGRESS_STATUS_LANG;
                                }
                            } else {
                                wipStatus = WORKINPROGRESS_STATUS_LANG;
                            }
                        }
                        switch (wipStatus) {
                            case WORKINPROGRESS_STATUS_DISABLED:
                                if (targetNode.hasProperty(WORKINPROGRESS_STATUS) && !WORKINPROGRESS_STATUS_DISABLED.equals(targetNode.getProperty(WORKINPROGRESS_STATUS).getString())) {
                                    targetNode.setProperty(WORKINPROGRESS_LANGUAGES, (Value[]) null);
                                    targetNode.setProperty(WORKINPROGRESS_STATUS, (Value) null);
                                } else {
                                    needSave = false;
                                }
                                if (debugEnabled) {
                                    logger.debug("Removing WIP status property on node {}", targetNode.getPath());
                                }
                                break;
                            case WORKINPROGRESS_STATUS_ALLCONTENT:
                                targetNode.setProperty(WORKINPROGRESS_STATUS, WORKINPROGRESS_STATUS_ALLCONTENT);
                                targetNode.setProperty(WORKINPROGRESS_LANGUAGES, (Value[]) null);
                                if (debugEnabled) {
                                    logger.debug("Setting WIP languages on node {} to {}", targetNode.getPath(),
                                            wipLanguagesToSet);
                                }
                                break;
                            case WORKINPROGRESS_STATUS_LANG:
                                targetNode.setProperty(WORKINPROGRESS_STATUS, WORKINPROGRESS_STATUS_LANG);
                                targetNode.setProperty(WORKINPROGRESS_LANGUAGES,
                                        JCRContentUtils.createValues(wipLanguagesToSet, systemSession.getValueFactory()));
                                if (debugEnabled) {
                                    logger.debug("Setting WIP languages on node {} to {}", targetNode.getPath(),
                                            wipLanguagesToSet);
                                }
                        }
                        if (needSave)
                            targetNode.getSession().save();
                        return null;
                    }
                });
    }

    private String getSessionLocale(JCRNodeWrapper node) throws RepositoryException {
        return LanguageCodeConverters.localeToLanguageTag(node.getSession().getLocale());
    }

    private Set<String> getSiteLanguages(JCRNodeWrapper node) throws RepositoryException {
        return node.getResolveSite().getLanguages();
    }
}
