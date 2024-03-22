package edu.ohsu.cmp.ecp.util;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IIdType;

import java.util.*;

public class IIdTypeUtil {

	public static String toString(Optional<IIdType> optional) {
		return optional.map(IIdTypeUtil::toString).orElse("");
	}

	public static String toString(Collection<IIdType> collection) {
		if (collection == null || collection.isEmpty()) {
			return "[]";
		}
		List<String> list = new ArrayList<>();
		for (IIdType iIdType : collection) {
			list.add(toString(iIdType));
		}
		return "[" + StringUtils.join(list, ", ") + "]";
	}

	public static String toString(IIdType idType) {
		if (idType == null) return "null";

		return "IIdType{" +
			"value='" + idType.getValue() + '\'' +
			'}';
	}
}
