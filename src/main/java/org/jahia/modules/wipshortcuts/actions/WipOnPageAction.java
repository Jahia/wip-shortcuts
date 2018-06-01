package org.jahia.modules.wipshortcuts.actions;

import org.jahia.api.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

public class WipOnPageAction extends AbstractWipUpdaterAction {

    private static final Logger logger = LoggerFactory.getLogger(WipOnPageAction.class);

    @Override
    protected boolean isValidRootNode(JCRNodeWrapper node) throws RepositoryException {
        return node.isNodeType(Constants.JAHIANT_PAGE);
    }

    @Override
    protected boolean canIterate(JCRNodeWrapper child) {
        try {
            return !child.isNodeType(Constants.JAHIANT_PAGE) && child.isNodeType(Constants.JAHIANT_CONTENT);
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }
}
