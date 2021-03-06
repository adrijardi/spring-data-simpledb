/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.springframework.data.simpledb.attributeutil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AttributesKeySplitter {

	private AttributesKeySplitter() {
		/* utility class */
	}

	public static Map<String, Map<String, String>> splitNestedAttributeKeys(Map<String, String> attributes) {
		final Map<String, Map<String, String>> nestedFieldAttributes = new HashMap<String, Map<String, String>>();

		for(final Map.Entry<String, String> entry : attributes.entrySet()) {
			final String key = entry.getKey();

			if(key.contains(".")) {
				Map<String, String> nestedFieldValues = new HashMap<String, String>();
				int prefixIndex = key.indexOf('.');
				final String nestedFieldName = key.substring(0, prefixIndex);
				final String subField = key.substring(prefixIndex + 1);

				if(nestedFieldAttributes.containsKey(nestedFieldName)) {
					nestedFieldValues = nestedFieldAttributes.get(nestedFieldName);
				}

				nestedFieldValues.put(subField, entry.getValue());

				nestedFieldAttributes.put(nestedFieldName, nestedFieldValues);
			}
		}
		return nestedFieldAttributes;
	}

	public static Map<String, String> splitSimpleAttributesKeys(Map<String, String> attributes) {

		Map<String, String> primitiveAttributes = new LinkedHashMap<String, String>();

		for(final Map.Entry<String, String> entry : attributes.entrySet()) {
			if(isSimpleKey(entry.getKey())) {
				primitiveAttributes.put(entry.getKey(), entry.getValue());
			}
		}

		return primitiveAttributes;

	}

	private static boolean isSimpleKey(final String key) {
		return !key.contains(".");
	}

}
