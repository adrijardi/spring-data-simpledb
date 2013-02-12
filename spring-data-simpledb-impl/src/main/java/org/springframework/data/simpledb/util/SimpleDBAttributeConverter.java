package org.springframework.data.simpledb.util;

import org.springframework.util.Assert;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;

public final class SimpleDBAttributeConverter {

    public static final int LONG_DIGITS = 20;
    public static final BigDecimal OFFSET_VALUE = new BigDecimal(Long.MIN_VALUE).negate();

    private SimpleDBAttributeConverter() {
    	/* utility class */
    }
    
    private static String padOrConvertIfRequired(Object ob) {

        if (ob instanceof Number && !(ob instanceof Float || ob instanceof Double)) {
            // then pad
            return AmazonSimpleDBUtil.encodeRealNumberRange(new BigDecimal(ob.toString()), AmazonSimpleDBUtil.LONG_DIGITS, OFFSET_VALUE);
        } else if ((ob instanceof Double && !((Double) ob).isInfinite() && !((Double) ob).isNaN()) || (ob instanceof Float && !((Float) ob).isInfinite() && !((Float) ob).isNaN())) {
            // then pad
            return AmazonSimpleDBUtil.encodeRealNumberRange(new BigDecimal(ob.toString()), AmazonSimpleDBUtil.LONG_DIGITS, AmazonSimpleDBUtil.LONG_DIGITS, OFFSET_VALUE);
        } else if (ob instanceof BigDecimal) {
            // then pad
            return AmazonSimpleDBUtil.encodeRealNumberRange((BigDecimal) ob, AmazonSimpleDBUtil.LONG_DIGITS, AmazonSimpleDBUtil.LONG_DIGITS, OFFSET_VALUE);
        } else if (ob instanceof Date) {
            Date d = (Date) ob;
            return AmazonSimpleDBUtil.encodeDate(d);
        } else if (ob instanceof byte[]) {
            return AmazonSimpleDBUtil.encodeByteArray((byte[]) ob);
        }

        return ob.toString();
    }

    public static String toSimpleDBAttributeValue(final Object fieldValue) {
        return padOrConvertIfRequired(fieldValue);
    }

    public static List<String> primitiveArraysToSimpleDBAttributeValues(final Object primitiveCollectionFieldValues) {
        Assert.notNull(primitiveCollectionFieldValues);

        final List<String> attributeValues = new ArrayList<>();
        final int primitiveCollLength = Array.getLength(primitiveCollectionFieldValues);

        for (int idx = 0; idx < primitiveCollLength; idx++) {
            Object itemValue = Array.get(primitiveCollectionFieldValues, idx);
            attributeValues.add(padOrConvertIfRequired(itemValue));
        }

        return attributeValues;
    }


    public static Object toFieldOfType(String value, Class<?> retType) throws ParseException {
        Object val = null;

        if (Integer.class.isAssignableFrom(retType) || retType == int.class) {
            val = AmazonSimpleDBUtil.decodeRealNumberRange(value, OFFSET_VALUE).intValue();
        } else if (Long.class.isAssignableFrom(retType) || retType == long.class) {
            val = AmazonSimpleDBUtil.decodeRealNumberRange(value, OFFSET_VALUE).longValue();
        }
        if (Short.class.isAssignableFrom(retType) || retType == short.class) {
            val = AmazonSimpleDBUtil.decodeRealNumberRange(value, OFFSET_VALUE).shortValue();
        } else if (Byte.class.isAssignableFrom(retType) || retType == byte.class) {
            val = AmazonSimpleDBUtil.decodeRealNumberRange(value, OFFSET_VALUE).byteValue();
        } else if (Float.class.isAssignableFrom(retType) || retType == float.class) {
            // Ignore NaN and Infinity
            if (!value.matches(".*Infinity|NaN")) {
                val = AmazonSimpleDBUtil.decodeRealNumberRange(value, AmazonSimpleDBUtil.LONG_DIGITS, OFFSET_VALUE).floatValue();
            } else {
                val = Float.NaN;
            }
        } else if (Double.class.isAssignableFrom(retType) || retType == double.class) {
            // Ignore NaN and Infinity
            if (!value.matches(".*Infinity|NaN")) {
                val = AmazonSimpleDBUtil.decodeRealNumberRange(value, AmazonSimpleDBUtil.LONG_DIGITS, OFFSET_VALUE).doubleValue();
            } else {
                val = Double.NaN;
            }
        } else if (BigDecimal.class.isAssignableFrom(retType)) {
            val = AmazonSimpleDBUtil.decodeRealNumberRange(value, AmazonSimpleDBUtil.LONG_DIGITS, OFFSET_VALUE);
        } else if (byte[].class.isAssignableFrom(retType)) {
            val = AmazonSimpleDBUtil.decodeByteArray(value);
        } else if (Date.class.isAssignableFrom(retType)) {
            val = AmazonSimpleDBUtil.decodeDate(value);
        } else if (Boolean.class.isAssignableFrom(retType) || retType == boolean.class) {
            val = Boolean.parseBoolean(value);
        } else if (String.class.isAssignableFrom(retType)) {
            val = value;
        }

        return val;
    }

    public static Object toDomainFieldPrimitiveArrays(List<String> fromSimpleDbAttValues, Class<?> retType) throws ParseException {
        Object primitiveCollection = Array.newInstance(retType, fromSimpleDbAttValues.size());
        int idx = 0;

        for (Iterator<String> iterator = fromSimpleDbAttValues.iterator(); iterator.hasNext(); idx++) {
            Array.set(primitiveCollection, idx, toFieldOfType(iterator.next(), retType));
        }

        return primitiveCollection;
    }
}