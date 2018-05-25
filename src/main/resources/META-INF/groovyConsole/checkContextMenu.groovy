import org.apache.commons.beanutils.PropertyUtils
import org.jahia.services.SpringContextSingleton

def editmode = SpringContextSingleton.getBean("editmode")
def menu = PropertyUtils.getNestedProperty(editmode, "tabs[2].tableContextMenu")
menu.items.each() {
    item -> log.info(item.titleKey)
}