package org.example;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;

public class RCLFilterTranslator extends AbstractFilterTranslator<String> {
    protected String createEqualsExpression(EqualsFilter filter, boolean not) {
        Attribute attribute = filter.getAttribute();
        String response = null;
        if (attribute.getName().equals(Uid.NAME)) {
            response =" userName="  + AttributeUtil.getAsStringValue(attribute);
        } else {
            System.out.println(attribute.getName() +" from Filter Xlator");
            System.out.println("Cannot be performed");
        }
        return response;
    }
}
