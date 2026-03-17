package com.sorted.common.helper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sorted.common.enums.Operators;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class AggregationFilter {

	private static final Logger logger = LoggerFactory.getLogger(AggregationFilter.class);

	@Getter
	public static class SEFilter {

		private List<WhereClause> clause;
		private SEFilterType type;
		private List<SEFilterNode> nodes;
//		private SEFilterType subquery_type;
		@Setter
        private OrderBy orderBy;
		@Setter
        private Pagination pagination;
		private List<String> selection;

		// Don't use below
		private List<JoinClause> joins;
		private List<SubqueryClause> subqueries;

        public SEFilter(SEFilterType type) {
			this.type = type;
		}

		public void addClause(List<WhereClause> clause) {
			if (CollectionUtils.isEmpty(this.clause)) {
				this.clause = new ArrayList<>();
			}
			this.clause.addAll(clause);
		}

		public void addClause(WhereClause clause) {
			if (CollectionUtils.isEmpty(this.clause)) {
				this.clause = new ArrayList<>();
			}
			this.clause.add(clause);
		}

		public void addNodes(List<SEFilterNode> nodes) {
			if (CollectionUtils.isEmpty(this.nodes)) {
				this.nodes = new ArrayList<>();
			}
			this.nodes.addAll(nodes);
		}

		public void addNodes(SEFilterNode nodes) {
			if (CollectionUtils.isEmpty(this.nodes)) {
				this.nodes = new ArrayList<>();
			}
			this.nodes.add(nodes);
		}

		public void addProjection(String... fields) {
			if (CollectionUtils.isEmpty(selection)) {
				this.selection = new ArrayList<>();
			}
            Collections.addAll(this.selection, fields);
		}
	}

	@Data
	public static class SEFilterNode {
		private List<WhereClause> clause;
		private SEFilterType type;
		private SEFilter subQuertClause;

		public SEFilterNode(SEFilterType type) {
			this.type = type;
		}

		public void addClause(List<WhereClause> clause) {
			if (CollectionUtils.isEmpty(this.clause)) {
				this.clause = new ArrayList<>();
			}
			this.clause.addAll(clause);
		}

		public void addClause(WhereClause clause) {
			if (CollectionUtils.isEmpty(this.clause)) {
				this.clause = new ArrayList<>();
			}
			this.clause.add(clause);
		}
	}

	@Getter
	public static class WhereClause {
		private final String field;
		private String value;
		private final String valueClassType;
		private List<?> valueList;
		private Map<String, String> keyMap;
		private Map<String, List<?>> valMap;
		private Map<String, Object> elemMap;
		private final Operators operator;

		private WhereClause(@NonNull String field, @NonNull List<?> valueList, @NonNull Operators operator) {
			super();
			this.field = field;
			this.operator = operator;
			if (CollectionUtils.isEmpty(valueList)) {
				throw new CustomIllegalArgumentsException(ResponseCode.NOT_A_LIST);
			}
			this.valueClassType = "list";
			this.valueList = valueList;
		}

		private WhereClause(@NonNull String field, @NonNull Map<String, Object> map) {
			super();
			this.field = field;
			this.operator = Operators.ELEMMATCH_IN;
			if (CollectionUtils.isEmpty(map)) {
				throw new CustomIllegalArgumentsException(ResponseCode.NOT_A_MAP);
			}
			this.valueClassType = "Map";
			this.elemMap = map;
		}

		private WhereClause(@NonNull String field, @NonNull Operators operator) {
			super();
			this.field = field;
			this.operator = operator;
			this.valueClassType = "Map";
		}

		private WhereClause(@NonNull String field, Object value, @NonNull Operators operator) {
			this.field = field;
			this.operator = operator;

			if (value != null) {
				Class<?> pValClass = value.getClass();
				this.valueClassType = pValClass.getSimpleName();

				// converting value
				if (pValClass == String.class) {
					this.value = (String) value;
				} else if (pValClass == java.time.LocalDateTime.class || pValClass == java.time.LocalDate.class) {
					this.value = String.valueOf(value);
				} else if (pValClass == int.class || pValClass == Integer.class || pValClass == long.class
						|| pValClass == Long.class || pValClass == double.class || pValClass == Double.class
						|| pValClass == boolean.class || pValClass == Boolean.class || pValClass == BigDecimal.class) {
					this.value = String.valueOf(value);
				} else {
					this.value = null;
				}
			} else {
				this.value = null;
				this.valueClassType = null;
			}
		}

		public static WhereClause eq(String field, Object value) {
			return new WhereClause(field, value, Operators.EQUALS);
		}

		public static WhereClause notEq(String field, Object value) {
			return new WhereClause(field, value, Operators.NOT_EQUALS);
		}

		public static WhereClause like(String field, Object value) {
			return new WhereClause(field, value, Operators.LIKE);
		}

		public static WhereClause notLike(String field, Object value) {
			return new WhereClause(field, value, Operators.NOT_LIKE);
		}

		public static WhereClause in(String field, List<?> value) {
			return new WhereClause(field, value, Operators.IN);
		}

		public static WhereClause nin(String field, List<?> value) {
			return new WhereClause(field, value, Operators.NIN);
		}

		public static WhereClause all(String field, List<?> value) {
			return new WhereClause(field, value, Operators.ALL);
		}

		public static WhereClause gte(String field, Object value) {
			return new WhereClause(field, value, Operators.GTE);
		}

		public static WhereClause lte(String field, Object value) {
			return new WhereClause(field, value, Operators.LTE);
		}

		public static WhereClause gt(String field, Object value) {
			return new WhereClause(field, value, Operators.GT);
		}

		public static WhereClause lt(String field, Object value) {
			return new WhereClause(field, value, Operators.LT);
		}

		public static WhereClause isNull(String field) { return new WhereClause(field, Operators.IS_NULL); }

		public static WhereClause isNotNull(String field) { return new WhereClause(field, Operators.IS_NOT_NULL); }

		public static WhereClause isEmpty(String field) { return new WhereClause(field, Operators.IS_EMPTY); }

		public static WhereClause isNotEmpty(String field) { return new WhereClause(field, Operators.IS_NOT_EMPTY); }

//		public static WhereClause elem_match(String field, Map<String, String> keyMap, Map<String, List<?>> valMap) {
//			return new WhereClause(field, keyMap, valMap);
//		}

		public static WhereClause elem_match(String field, Map<String, Object> keyMap) {
			return new WhereClause(field, keyMap);
		}

		@JsonIgnore
		private static boolean isValuePrimitive(String invalueClassType) {
			if (StringUtils.hasLength(invalueClassType)) {
				List<String> arrPrimitives = Arrays.asList("String", "int", "Integer", "long", "Long", "double",
						"Double", "boolean", "Boolean", "BigDecimal", "LocalDateTime", "LocalDate");
				return arrPrimitives.contains(invalueClassType);
			}
			return false;
		}

		@JsonIgnore
		public Object getValueAsObject() {
			if (WhereClause.isValuePrimitive(this.valueClassType) && StringUtils.hasLength(this.value)) {
				try {

					switch (this.valueClassType) {
					case "String":
						return this.value;

					case "int":
					case "Integer":
						return Integer.parseInt(this.value);

					case "long":
					case "Long":
						return Long.parseLong(this.value);

					case "double":
					case "Double":
						return Double.parseDouble(this.value);

					case "boolean":
					case "Boolean":
						return Boolean.parseBoolean(this.value);

					case "BigDecimal":
						return BigDecimal.valueOf(Double.parseDouble(this.value));

					case "LocalDateTime":
						return LocalDateTime.parse(this.value);
					case "LocalDate":
						return LocalDate.parse(this.value);
					default:
						return null;
					}
				} catch (Exception e) {
					logger.error(e.getMessage());
					return null;
				}
			}
			return null;
		}

	}

	@Getter
	@AllArgsConstructor
	public enum SEFilterType {
		AND, OR
	}

	@Getter
	@AllArgsConstructor
	public enum SortOrder {
		ASC, DESC
	}

	@Data
	public static class OrderBy {
		public OrderBy(String key, SortOrder sortOrder) {
			this.type = sortOrder;
			this.key = key;
		}

		private SortOrder type = SortOrder.DESC;
		private String key;
	}

	@Data
	public static class JoinClause {
		private JoinType joinType; // INNER, LEFT, etc.
		private String joinOnField; // Field to join on
		private String joinToEntity; // Entity to join with
	}

	@Getter
	@AllArgsConstructor
	public enum JoinType {
		INNER, LEFT, RIGHT
	}

	@Data
	public static class SubqueryClause {
		private AggregationFilter subqueryFilter;
		private String correlatedField; // Field in the main query used for correlation
	}

//	public Object getQuery(SEFilter f) {
//		
//		f.getClause();
//		if(!CollectionUtils.isEmpty(f.nodes)){
//			for (SEFilterNode node : f.nodes) {
//				getQuery(node.getSubQuertClause());
//			}
//		}
//		return null;
//	}

}
