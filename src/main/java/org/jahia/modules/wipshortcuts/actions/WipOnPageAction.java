package org.jahia.modules.wipshortcuts.actions;

import org.jahia.api.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

public class WipOnPageAction extends AbstractWipUpdaterAction {

    private static final Logger logger = LoggerFactory.getLogger(WipOnPageAction.class);

    @Override
    protected boolean isValidRootNode(JCRNodeWrapper node) {
        try {
            return node.isNodeType(Constants.JAHIANT_PAGE);
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }

    @Override
    protected boolean canIterate(JCRNodeWrapper child) {
        try {
            return !isValidRootNode(child) && child.isNodeType(Constants.JAHIANT_CONTENT);
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }
}
