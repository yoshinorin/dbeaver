/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2022 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ext.postgresql.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ext.postgresql.PostgreConstants;
import org.jkiss.dbeaver.ext.postgresql.PostgreUtils;
import org.jkiss.dbeaver.ext.postgresql.model.data.PostgreBinaryFormatter;
import org.jkiss.dbeaver.ext.postgresql.sql.PostgreEscapeStringRule;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;
import org.jkiss.dbeaver.model.data.DBDBinaryFormatter;
import org.jkiss.dbeaver.model.exec.DBCLogicalOperator;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCDatabaseMetaData;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSource;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCSQLDialect;
import org.jkiss.dbeaver.model.impl.sql.BasicSQLDialect;
import org.jkiss.dbeaver.model.sql.SQLDataTypeConverter;
import org.jkiss.dbeaver.model.sql.SQLDialect;
import org.jkiss.dbeaver.model.sql.SQLExpressionFormatter;
import org.jkiss.dbeaver.model.sql.parser.rules.SQLDollarQuoteRule;
import org.jkiss.dbeaver.model.struct.DBSAttributeBase;
import org.jkiss.dbeaver.model.struct.DBSDataType;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;
import org.jkiss.dbeaver.model.struct.DBSTypedObjectEx;
import org.jkiss.dbeaver.model.text.parser.TPRule;
import org.jkiss.dbeaver.model.text.parser.TPRuleProvider;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.utils.ArrayUtils;
import org.jkiss.utils.CommonUtils;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * PostgreSQL dialect
 */
public class PostgreDialect extends JDBCSQLDialect implements TPRuleProvider, SQLDataTypeConverter {
    public static final String[] POSTGRE_NON_TRANSACTIONAL_KEYWORDS = ArrayUtils.concatArrays(
        BasicSQLDialect.NON_TRANSACTIONAL_KEYWORDS,
        new String[]{
            "SHOW", "SET"
        }
    );

    private static final String[][] PG_STRING_QUOTES = {
        {"'", "'"}
    };

    // In PgSQL there are no blocks. DO $$ ... $$ queries are processed as strings
    public static final String[][] BLOCK_BOUND_KEYWORDS = {
//        {SQLConstants.BLOCK_BEGIN, SQLConstants.BLOCK_END},
//        {"LOOP", "END LOOP"}
    };

    private static final String[] EXEC_KEYWORDS = {
        "CALL"
    };

    //Function without arguments/parameters #8710
    private static final String[] OTHER_TYPES_FUNCTION = {
        "CURRENT_DATE",
        "CURRENT_TIME",
        "CURRENT_TIMESTAMP",
        "CURRENT_ROLE",
        "CURRENT_USER",
    };

    //region KeyWords

    public static String[] POSTGRE_EXTRA_KEYWORDS = new String[]{
        "ABSENT",
        "ACCORDING",
        "ADA",
        "ADMIN",
//            "ARRAY_AGG",
//            "ARRAY_MAX_CARDINALITY",
        "BASE64",
        "BEGIN_FRAME",
        "BEGIN_PARTITION",
        "BERNOULLI",
        "BIT_LENGTH",
        "BLOCKED",
        "BOM",
        //"BREADTH",
//            "CATALOG_NAME",
//            "CHARACTER_SET_CATALOG",
//            "CHARACTER_SET_NAME",
//            "CHARACTER_SET_SCHEMA",
//            "CLASS_ORIGIN",
        //"COBOL",
//            "COLLATION_CATALOG",
//            "COLLATION_NAME",
//            "COLLATION_SCHEMA",
//            "COLUMN_NAME",
//            "COMMAND_FUNCTION",
//            "COMMAND_FUNCTION_CODE",
//            "CONDITION_NUMBER",
//            "CONNECTION_NAME",
//            "CONSTRAINT_CATALOG",
//            "CONSTRAINT_NAME",
//            "CONSTRAINT_SCHEMA",
        "CONTROL",
//            "CURRENT_ROW",
//            "DATALINK",
//            "DATETIME_INTERVAL_CODE",
//            "DATETIME_INTERVAL_PRECISION",
        //"DB",
        "DLNEWCOPY",
        "DLPREVIOUSCOPY",
        "DLURLCOMPLETE",
        "DLURLCOMPLETEONLY",
        "DLURLCOMPLETEWRITE",
        "DLURLPATH",
        "DLURLPATHONLY",
        "DLURLPATHWRITE",
        "DLURLSCHEME",
        "DLURLSERVER",
        "DLVALUE",
        "DYNAMIC_FUNCTION",
        "DYNAMIC_FUNCTION_CODE",
        "EMPTY",
        "END_FRAME",
        "END_PARTITION",
        "ENFORCED",
        "EXIT",
        "EXPRESSION",
        //"FILE",
        "FIRST_VALUE",
        //"FLAG",
        //"FORTRAN",
        "FRAME_ROW",
        "FS",
        "GROUPS",
        //"HEX",
        //"ID",
        "IGNORE",
        "IMMEDIATELY",
        "INCLUDE",
        "INDENT",
        "INTEGRITY",
        "KEY_MEMBER",
        "LAG",
        "LAST_VALUE",
        "LEAD",
        "LIBRARY",
        "LIKE_REGEX",
        //"LINK",
//            "MAX_CARDINALITY",
//            "MESSAGE_LENGTH",
//            "MESSAGE_OCTET_LENGTH",
//            "MESSAGE_TEXT",
        //"MODULE",
        //"NAME",
        //"NAMES",
        "NAMESPACE",
        //"NFC",
        //"NFD",
        //"NFKC",
        //"NFKD",
        "NIL",
        "NTH_VALUE",
        "NTILE",
        "NULLABLE",
        "OCCURRENCES_REGEX",
//            "PARAMETER_MODE",
//            "PARAMETER_NAME",
//            "PARAMETER_ORDINAL_POSITION",
//            "PARAMETER_SPECIFIC_CATALOG",
//            "PARAMETER_SPECIFIC_NAME",
//            "PARAMETER_SPECIFIC_SCHEMA",
        //"PASCAL",
        "PASSTHROUGH",
        "PERCENT",
        "PERIOD",
        "PERMISSION",
        //"PLI",
        //"PORTION",
        "POSITION_REGEX",
        "PRECEDES",
        "PROCEDURES",
        //"PUBLIC",
        "RECOVERY",
        "REQUIRING",
        "RESPECT",
        "RESTORE",
        "RULE",
//            "RETURNED_CARDINALITY",
//            "RETURNED_LENGTH",
//            "RETURNED_OCTET_LENGTH",
//            "RETURNED_SQLSTATE",
//            "ROUTINES",
//            "ROUTINE_CATALOG",
//            "ROUTINE_NAME",
//            "ROUTINE_SCHEMA",
        //"ROW_COUNT",
        //"SCHEMA_NAME",
        //"SCOPE_CATALOG",
        //"SCOPE_NAME",
        //"SCOPE_SCHEMA",
        //"SELECTIVE",
        //"SERVER_NAME",
        "SIMPLE",
        //"SPECIFIC_NAME",
        "SQLCODE",
        "SQLERROR",
        //"STATE",
        //"SUBCLASS_ORIGIN",
        //"SUBSTRING_REGEX",
        "SUCCEEDS",
        //"SYSTEM_TIME",
        //"TABLE_NAME",
        "TOKEN",
        //"TOP_LEVEL_COUNT",
        //"TRANSACTIONS_COMMITTED",
        //"TRANSACTIONS_ROLLED_BACK",
        //"TRANSACTION_ACTIVE",
        //"TRANSLATE_REGEX",
        //"TRIGGER_CATALOG",
        //"TRIGGER_NAME",
        //"TRIGGER_SCHEMA",
        //"TRIM_ARRAY",
        "UNLINK",
        "UNTYPED",
        //"URI",
        //"USER_DEFINED_TYPE_CATALOG",
        //"USER_DEFINED_TYPE_CODE",
        //"USER_DEFINED_TYPE_NAME",
        //"USER_DEFINED_TYPE_SCHEMA",
        //"VALUE",
        //"VALUE_OF",
        "VERSIONING",
        "XMLAGG",
        "XMLBINARY",
        "XMLCAST",
        "XMLCOMMENT",
        "XMLDECLARATION",
        "XMLDOCUMENT",
        "XMLITERATE",
        "XMLQUERY",
        "XMLSCHEMA",
        "XMLTEXT",
        "XMLVALIDATE",
        "SQLERRM",
        "WHILE"
    };

    public static String[] POSTGRE_ONE_CHAR_KEYWORDS = new String[]{
        "C",
        "G",
        "K",
        "M",
        "T",
        "P"
    };
    //endregion

    //region FUNCTIONS KW

    public static String[] POSTGRE_FUNCTIONS_AGGREGATE = new String[]{
        "ARRAY_AGG",
        "BIT_AND",
        "BIT_OR",
        "BOOL_AND",
        "BOOL_OR",
        "EVERY",
        "JSON_AGG",
        "JSONB_AGG",
        "JSON_OBJECT_AGG",
        "JSONB_OBJECT_AGG",
        "MODE",
        "STRING_AGG",
        "XMLAGG",
        "CORR",
        "COVAR_POP",
        "COVAR_SAMP",
        "STDDEV",
        "STDDEV_POP",
        "STDDEV_SAMP",
        "VARIANCE",
        "VAR_POP",
        "VAR_SAMP"
    };

    public static String[] POSTGRE_FUNCTIONS_WINDOW = new String[]{
        "ROW_NUMBER",
        "RANK",
        "DENSE_RANK",
        "CUME_DIST",
        "NTILE",
        "LAG",
        "LEAD",
        "FIRST_VALUE",
        "LAST_VALUE",
        "NTH_VALUE"
    };


    public static String[] POSTGRE_FUNCTIONS_MATH = new String[]{
        "ACOSD",
        "ASIND",
        "ATAN2D",
        "ATAND",
        "CBRT",
        "CEIL",
        "CEILING",
        "COSD",
        "COTD",
        "DIV",
        "EXP",
        "LN",
        "MOD",
        "RANDOM",
        "SCALE",
        "SETSEED",
        "SIND",
        "TAND",
        "TRUNC",
        "WIDTH_BUCKET"
    };
    public static String[] POSTGRE_FUNCTIONS_STRING = new String[]{
        "BIT_LENGTH",
        "BTRIM",
        "CHR",
        "CONCAT_WS",
        "CONVERT",
        "CONVERT_FROM",
        "CONVERT_TO",
        "DECODE",
        "ENCODE",
        "INITCAP",
        "LEFT",
        "LENGTH",
        "LPAD",
        "MD5",
        "OVERLAY",
        "PARSE_IDENT",
        "PG_CLIENT_ENCODING",
        "POSITION",
        "QUOTE_IDENT",
        "QUOTE_LITERAL",
        "QUOTE_NULLABLE",
        "REGEXP_MATCH",
        "REGEXP_MATCHES",
        "REGEXP_REPLACE",
        "REGEXP_SPLIT_TO_ARRAY",
        "REGEXP_SPLIT_TO_TABLE",
        "REPLACE",
        "REVERSE",
        "RIGHT",
        "RPAD",
        "SPLIT_PART",
        "STRPOS",
        "SUBSTRING",
        "TO_ASCII",
        "TO_HEX",
        "TRANSLATE",
        "TREAT",
    };

    public static String[] POSTGRE_FUNCTIONS_DATETIME = new String[]{
        "AGE",
        "CLOCK_TIMESTAMP",
        "DATE_PART",
        "DATE_TRUNC",
        "ISFINITE",
        "JUSTIFY_DAYS",
        "JUSTIFY_HOURS",
        "JUSTIFY_INTERVAL",
        "MAKE_DATE",
        "MAKE_INTERVAL",
        "MAKE_TIME",
        "MAKE_TIMESTAMP",
        "MAKE_TIMESTAMPTZ",
        "STATEMENT_TIMESTAMP",
        "TIMEOFDAY",
        "TRANSACTION_TIMESTAMP"
    };

    public static String[] POSTGRE_FUNCTIONS_GEOMETRY = new String[]{
        "AREA",
        "CENTER",
        "DIAMETER",
        "HEIGHT",
        "ISCLOSED",
        "ISOPEN",
        "NPOINTS",
        "PCLOSE",
        "POPEN",
        "RADIUS",
        "WIDTH",
        "BOX",
        "BOUND_BOX",
        "CIRCLE",
        "LINE",
        "LSEG",
        "PATH",
        "POLYGON"
    };

    public static String[] POSTGRE_FUNCTIONS_NETWROK = new String[]{
        "ABBREV",
        "BROADCAST",
        "HOST",
        "HOSTMASK",
        "MASKLEN",
        "NETMASK",
        "NETWORK",
        "SET_MASKLEN",
        "TEXT",
        "INET_SAME_FAMILY",
        "INET_MERGE",
        "MACADDR8_SET7BIT"
    };

    public static String[] POSTGRE_FUNCTIONS_LO = new String[]{
        "LO_FROM_BYTEA",
        "LO_PUT",
        "LO_GET",
        "LO_CREAT",
        "LO_CREATE",
        "LO_UNLINK",
        "LO_IMPORT",
        "LO_EXPORT",
        "LOREAD",
        "LOWRITE",
        "GROUPING",
        "CAST"
    };

    public static String[] POSTGRE_FUNCTIONS_ADMIN = new String[]{
        "CURRENT_SETTING",
        "SET_CONFIG",
        "BRIN_SUMMARIZE_NEW_VALUES",
        "BRIN_SUMMARIZE_RANGE",
        "BRIN_DESUMMARIZE_RANGE",
        "GIN_CLEAN_PENDING_LIST"
    };

    public static String[] POSTGRE_FUNCTIONS_RANGE = new String[]{
        "ISEMPTY",
        "LOWER_INC",
        "UPPER_INC",
        "LOWER_INF",
        "UPPER_INF",
        "RANGE_MERGE"
    };

    public static String[] POSTGRE_FUNCTIONS_TEXT_SEARCH = new String[]{
        "ARRAY_TO_TSVECTOR",
        "GET_CURRENT_TS_CONFIG",
        "NUMNODE",
        "PLAINTO_TSQUERY",
        "PHRASETO_TSQUERY",
        "WEBSEARCH_TO_TSQUERY",
        "QUERYTREE",
        "SETWEIGHT",
        "STRIP",
        "TO_TSQUERY",
        "TO_TSVECTOR",
        "JSON_TO_TSVECTOR",
        "JSONB_TO_TSVECTOR",
        "TS_DELETE",
        "TS_FILTER",
        "TS_HEADLINE",
        "TS_RANK",
        "TS_RANK_CD",
        "TS_REWRITE",
        "TSQUERY_PHRASE",
        "TSVECTOR_TO_ARRAY",
        "TSVECTOR_UPDATE_TRIGGER",
        "TSVECTOR_UPDATE_TRIGGER_COLUMN"
    };

    public static String[] POSTGRE_FUNCTIONS_XML = new String[]{
        "XMLCOMMENT",
        "XMLCONCAT",
        "XMLELEMENT",
        "XMLFOREST",
        "XMLPI",
        "XMLROOT",
        "XMLEXISTS",
        "XML_IS_WELL_FORMED",
        "XML_IS_WELL_FORMED_DOCUMENT",
        "XML_IS_WELL_FORMED_CONTENT",
        "XPATH",
        "XPATH_EXISTS",
        "XMLTABLE",
        "XMLNAMESPACES",
        "TABLE_TO_XML",
        "TABLE_TO_XMLSCHEMA",
        "TABLE_TO_XML_AND_XMLSCHEMA",
        "QUERY_TO_XML",
        "QUERY_TO_XMLSCHEMA",
        "QUERY_TO_XML_AND_XMLSCHEMA",
        "CURSOR_TO_XML",
        "CURSOR_TO_XMLSCHEMA",
        "SCHEMA_TO_XML",
        "SCHEMA_TO_XMLSCHEMA",
        "SCHEMA_TO_XML_AND_XMLSCHEMA",
        "DATABASE_TO_XML",
        "DATABASE_TO_XMLSCHEMA",
        "DATABASE_TO_XML_AND_XMLSCHEMA",
        "XMLATTRIBUTES"
    };

    public static String[] POSTGRE_FUNCTIONS_JSON = new String[]{
        "TO_JSON",
        "TO_JSONB",
        "ARRAY_TO_JSON",
        "ROW_TO_JSON",
        "JSON_BUILD_ARRAY",
        "JSONB_BUILD_ARRAY",
        "JSON_BUILD_OBJECT",
        "JSONB_BUILD_OBJECT",
        "JSON_OBJECT",
        "JSONB_OBJECT",
        "JSON_ARRAY_LENGTH",
        "JSONB_ARRAY_LENGTH",
        "JSON_EACH",
        "JSONB_EACH",
        "JSON_EACH_TEXT",
        "JSONB_EACH_TEXT",
        "JSON_EXTRACT_PATH",
        "JSONB_EXTRACT_PATH",
        "JSON_OBJECT_KEYS",
        "JSONB_OBJECT_KEYS",
        "JSON_POPULATE_RECORD",
        "JSONB_POPULATE_RECORD",
        "JSON_POPULATE_RECORDSET",
        "JSONB_POPULATE_RECORDSET",
        "JSON_ARRAY_ELEMENTS",
        "JSONB_ARRAY_ELEMENTS",
        "JSON_ARRAY_ELEMENTS_TEXT",
        "JSONB_ARRAY_ELEMENTS_TEXT",
        "JSON_TYPEOF",
        "JSONB_TYPEOF",
        "JSON_TO_RECORD",
        "JSONB_TO_RECORD",
        "JSON_TO_RECORDSET",
        "JSONB_TO_RECORDSET",
        "JSON_STRIP_NULLS",
        "JSONB_STRIP_NULLS",
        "JSONB_SET",
        "JSONB_INSERT",
        "JSONB_PRETTY"
    };

    public static String[] POSTGRE_FUNCTIONS_ARRAY = new String[]{
        "ARRAY_APPEND",
        "ARRAY_CAT",
        "ARRAY_NDIMS",
        "ARRAY_DIMS",
        "ARRAY_FILL",
        "ARRAY_LENGTH",
        "ARRAY_LOWER",
        "ARRAY_POSITION",
        "ARRAY_POSITIONS",
        "ARRAY_PREPEND",
        "ARRAY_REMOVE",
        "ARRAY_REPLACE",
        "ARRAY_TO_STRING",
        "ARRAY_UPPER",
        "CARDINALITY",
        "STRING_TO_ARRAY",
        "UNNEST"
    };

    public static String[] POSTGRE_FUNCTIONS_INFO = new String[]{
        "CURRENT_DATABASE",
        "CURRENT_QUERY",
        "CURRENT_SCHEMA",
        "CURRENT_SCHEMAS",
        "INET_CLIENT_ADDR",
        "INET_CLIENT_PORT",
        "INET_SERVER_ADDR",
        "INET_SERVER_PORT",
        "ROW_SECURITY_ACTIVE",
        "FORMAT_TYPE",
        "TO_REGCLASS",
        "TO_REGPROC",
        "TO_REGPROCEDURE",
        "TO_REGOPER",
        "TO_REGOPERATOR",
        "TO_REGTYPE",
        "TO_REGNAMESPACE",
        "TO_REGROLE",
        "COL_DESCRIPTION",
        "OBJ_DESCRIPTION",
        "SHOBJ_DESCRIPTION",
        "TXID_CURRENT",
        "TXID_CURRENT_IF_ASSIGNED",
        "TXID_CURRENT_SNAPSHOT",
        "TXID_SNAPSHOT_XIP",
        "TXID_SNAPSHOT_XMAX",
        "TXID_SNAPSHOT_XMIN",
        "TXID_VISIBLE_IN_SNAPSHOT",
        "TXID_STATUS"
    };

    public static String[] POSTGRE_FUNCTIONS_COMPRASION = new String[]{
        "NUM_NONNULLS",
        "NUM_NULLS"
    };

    public static String[] POSTGRE_FUNCTIONS_FORMATTING = new String[]{
        "TO_CHAR",
        "TO_DATE",
        "TO_NUMBER",
        "TO_TIMESTAMP"
    };

    public static String[] POSTGRE_FUNCTIONS_ENUM = new String[]{
        "ENUM_FIRST",
        "ENUM_LAST",
        "ENUM_RANGE"
    };

    public static String[] POSTGRE_FUNCTIONS_SEQUENCE = new String[]{
        "CURRVAL",
        "LASTVAL",
        "NEXTVAL",
        "SETVAL"
    };

    public static String[] POSTGRE_FUNCTIONS_BINARY_STRING = new String[]{
        "GET_BIT",
        "GET_BYTE",
        "SET_BIT",
        "SET_BYTE"
    };

    public static String[] POSTGRE_FUNCTIONS_CONDITIONAL = new String[]{
        "COALESCE",
        "NULLIF",
        "GREATEST",
        "LEAST"
    };

    public static String[] POSTGRE_FUNCTIONS_TRIGGER = new String[]{
        "SUPPRESS_REDUNDANT_UPDATES_TRIGGER"
    };

    public static String[] POSTGRE_FUNCTIONS_SRF = new String[]{
        "GENERATE_SERIES",
        "GENERATE_SUBSCRIPTS"
    };

    //endregion

    private PostgreServerExtension serverExtension;

    public PostgreDialect() {
        super("PostgreSQL", "postgresql");
    }

    public void addExtraKeywords(String... keywords) {
        super.addSQLKeywords(Arrays.asList(keywords));
    }

    public void addExtraFunctions(String... functions) {
        super.addFunctions(Arrays.asList(functions));
    }

    public void initDriverSettings(JDBCSession session, JDBCDataSource dataSource, JDBCDatabaseMetaData metaData) {
        super.initDriverSettings(session, dataSource, metaData);

        addExtraKeywords(
            "SHOW",
            "TYPE",
            "USER",
            "COMMENT",
            "MATERIALIZED",
            "ILIKE",
            "ELSIF",
            "ELSEIF",
            "ANALYSE",
            "ANALYZE",
            "CONCURRENTLY",
            "FREEZE",
            "LANGUAGE",
            "MODULE",
            "OFFSET",
            //"PUBLIC",
            "RETURNING",
            "VARIADIC",
            "PERFORM",
            "FOREACH",
            "LOOP",
            "PERFORM",
            "RAISE",
            "NOTICE",
            "CONFLICT",
            "EXTENSION",

            // "DEBUG", "INFO", "NOTICE", "WARNING", // levels
            // "MESSAGE", "DETAIL", "HINT", "ERRCODE", //options

            "DATATYPE",
            "TABLESPACE",
            "REFRESH"
        );

        addExtraKeywords(POSTGRE_EXTRA_KEYWORDS);
        // Not sure about one char keywords. May confuse users
        //addExtraKeywords(POSTGRE_ONE_CHAR_KEYWORDS);

        addKeywords(Arrays.asList(OTHER_TYPES_FUNCTION), DBPKeywordType.OTHER);

        addExtraFunctions(PostgreConstants.POSTGIS_FUNCTIONS);

        addExtraFunctions(POSTGRE_FUNCTIONS_ADMIN);
        addExtraFunctions(POSTGRE_FUNCTIONS_AGGREGATE);
        addExtraFunctions(POSTGRE_FUNCTIONS_ARRAY);
        addExtraFunctions(POSTGRE_FUNCTIONS_BINARY_STRING);
        addExtraFunctions(POSTGRE_FUNCTIONS_COMPRASION);
        addExtraFunctions(POSTGRE_FUNCTIONS_CONDITIONAL);
        addExtraFunctions(POSTGRE_FUNCTIONS_DATETIME);
        addExtraFunctions(POSTGRE_FUNCTIONS_ENUM);
        addExtraFunctions(POSTGRE_FUNCTIONS_FORMATTING);
        addExtraFunctions(POSTGRE_FUNCTIONS_GEOMETRY);
        addExtraFunctions(POSTGRE_FUNCTIONS_INFO);
        addExtraFunctions(POSTGRE_FUNCTIONS_JSON);
        addExtraFunctions(POSTGRE_FUNCTIONS_LO);
        addExtraFunctions(POSTGRE_FUNCTIONS_MATH);
        addExtraFunctions(POSTGRE_FUNCTIONS_NETWROK);
        addExtraFunctions(POSTGRE_FUNCTIONS_RANGE);
        addExtraFunctions(POSTGRE_FUNCTIONS_SEQUENCE);
        addExtraFunctions(POSTGRE_FUNCTIONS_SRF);
        addExtraFunctions(POSTGRE_FUNCTIONS_STRING);
        addExtraFunctions(POSTGRE_FUNCTIONS_TEXT_SEARCH);
        addExtraFunctions(POSTGRE_FUNCTIONS_TRIGGER);
        addExtraFunctions(POSTGRE_FUNCTIONS_WINDOW);
        addExtraFunctions(POSTGRE_FUNCTIONS_XML);

        removeSQLKeyword("LENGTH");

        if (dataSource instanceof PostgreDataSource) {
            serverExtension = ((PostgreDataSource) dataSource).getServerType();
            serverExtension.configureDialect(this);
        }

        // #12723 Redshift driver returns wrong infor about unquoted case
        setUnquotedIdentCase(DBPIdentifierCase.LOWER);
    }
    
    @Override
    public void addKeywords(Collection<String> set, DBPKeywordType type) {
        super.addKeywords(set, type);
    }

    @NotNull
    @Override
    public String[] getExecuteKeywords() {
        return EXEC_KEYWORDS;
    }

    @Override
    public char getStringEscapeCharacter() {
        if (serverExtension != null && serverExtension.supportsBackslashStringEscape()) {
            return '\\';
        }
        return super.getStringEscapeCharacter();
    }

    @Override
    public int getCatalogUsage() {
        return SQLDialect.USAGE_DML;
    }

    @Override
    public int getSchemaUsage() {
        return SQLDialect.USAGE_ALL;
    }

    @NotNull
    @Override
    public String[] getParametersPrefixes() {
        return new String[]{"$"};
    }

    @NotNull
    @Override
    public MultiValueInsertMode getDefaultMultiValueInsertMode() {
        return MultiValueInsertMode.GROUP_ROWS;
    }

    @Override
    public String[][] getBlockBoundStrings() {
        return BLOCK_BOUND_KEYWORDS;
    }

    @Override
    public String getCastedAttributeName(@NotNull DBSAttributeBase attribute) {
        // This method actually works for special data types like JSON and XML. Because column names in the condition in a table without key must be also cast, as data in getTypeCast method.
        String attrName = attribute.getName();
        if (attribute instanceof DBSObject) {
            attrName = DBUtils.isPseudoAttribute(attribute) ?
                attribute.getName() :
                DBUtils.getObjectFullName(((DBSObject) attribute).getDataSource(), attribute, DBPEvaluationContext.DML);
        }
        return getCastedString(attribute, attrName, true, true);
    }

    @NotNull
    @Override
    public String getTypeCastClause(@NotNull DBSTypedObject attribute, String expression, boolean isInCondition) {
        // Some data for some types of columns data types must be cast. It can be simple casting only with data type name like "::pg_class" or casting with fully qualified names for user defined types like "::schemaName.testType".
        // Or very special clauses with JSON and XML columns, when we have to cast both column data and column name to text.
        return getCastedString(attribute, expression, isInCondition, false);
    }

    private String getCastedString(@NotNull DBSTypedObject attribute, String string, boolean isInCondition, boolean castColumnName) {
        if (attribute instanceof DBSTypedObjectEx) {
            DBSDataType dataType = ((DBSTypedObjectEx) attribute).getDataType();
            if (dataType instanceof PostgreDataType) {
                String typeCasting = ((PostgreDataType) dataType).getConditionTypeCasting(isInCondition, castColumnName);
                if (CommonUtils.isNotEmpty(typeCasting)) {
                    return string + typeCasting;
                }
            }
        }
        return string;
    }

    @NotNull
    @Override
    public String escapeScriptValue(DBSTypedObject attribute, @NotNull Object value, @NotNull String strValue) {
        if (PostgreUtils.isPGObject(value) ||
            PostgreConstants.TYPE_BIT.equals(attribute.getTypeName()) ||
            PostgreConstants.TYPE_INTERVAL.equals(attribute.getTypeName()) ||
            attribute.getTypeID() == Types.OTHER)
        {
            // TODO: we need to add value handlers for all PG data types.
            // For now we use workaround: represent objects as strings
            return '\'' + escapeString(strValue) + '\'';
        }
        if (CommonUtils.isNaN(value) || CommonUtils.isInfinite(value)) {
            // These special values should be quoted
            // https://www.postgresql.org/docs/current/datatype-numeric.html#DATATYPE-NUMERIC-DECIMAL
            return '\'' + String.valueOf(value) + '\'';
        }
        return super.escapeScriptValue(attribute, value, strValue);
    }

    @NotNull
    @Override
    public String[][] getStringQuoteStrings() {
        return PG_STRING_QUOTES;
    }

    @Override
    public boolean supportsAliasInSelect() {
        return true;
    }

    @Override
    public boolean supportsTableDropCascade() {
        return true;
    }

    @Override
    public boolean supportsCommentQuery() {
        return true;
    }

    @Override
    public boolean supportsNestedComments() {
        return true;
    }

    @Nullable
    @Override
    public SQLExpressionFormatter getCaseInsensitiveExpressionFormatter(@NotNull DBCLogicalOperator operator) {
        if (operator == DBCLogicalOperator.LIKE) {
            return (left, right) -> left + " ILIKE " + right;
        }
        return super.getCaseInsensitiveExpressionFormatter(operator);
    }

    @NotNull
    @Override
    public DBDBinaryFormatter getNativeBinaryFormatter() {
        return PostgreBinaryFormatter.INSTANCE;
    }

    @Override
    protected void loadDataTypesFromDatabase(JDBCDataSource dataSource) {
        super.loadDataTypesFromDatabase(dataSource);
        addDataTypes(PostgreConstants.DATA_TYPE_ALIASES.keySet());
    }

    @NotNull
    @Override
    public String[] getNonTransactionKeywords() {
        return POSTGRE_NON_TRANSACTIONAL_KEYWORDS;
    }

    @Override
    protected boolean isStoredProcedureCallIncludesOutParameters() {
        return false;
    }

    @Override
    public void extendRules(@Nullable DBPDataSourceContainer dataSource, @NotNull List<TPRule> rules, @NotNull RulePosition position) {
        if (position == RulePosition.INITIAL || position == RulePosition.PARTITION) {
            boolean ddTagDefault = DBWorkbench.getPlatform().getPreferenceStore().getBoolean(PostgreConstants.PROP_DD_TAG_STRING);
            boolean ddTagIsString = dataSource == null
                ? ddTagDefault
                : CommonUtils.getBoolean(dataSource.getActualConnectionConfiguration().getProviderProperty(PostgreConstants.PROP_DD_TAG_STRING), ddTagDefault);

            boolean ddPlainDefault = DBWorkbench.getPlatform().getPreferenceStore().getBoolean(PostgreConstants.PROP_DD_PLAIN_STRING);
            boolean ddPlainIsString = dataSource == null
                ? ddPlainDefault
                : CommonUtils.getBoolean(dataSource.getActualConnectionConfiguration().getProviderProperty(PostgreConstants.PROP_DD_PLAIN_STRING), ddPlainDefault);

            rules.add(new SQLDollarQuoteRule(position == RulePosition.PARTITION, true, ddTagIsString, ddPlainIsString));
            rules.add(new PostgreEscapeStringRule());
        }
    }

    @Override
    public boolean supportsInsertAllDefaultValuesStatement() {
        return true;
    }

    @Override
    public String convertExternalDataType(@NotNull SQLDialect sourceDialect, @NotNull DBSTypedObject sourceTypedObject, @Nullable DBPDataTypeProvider targetTypeProvider) {
        String externalTypeName = sourceTypedObject.getTypeName().toLowerCase(Locale.ENGLISH);
        String localDataType = null, dataTypeModifies = null;

        switch (externalTypeName) {
            case "xml":
            case "xmltype":
            case "sys.xmltype":
                localDataType = "xml";
                break;
            case "varchar2":
            case "nchar":
            case "nvarchar":
                localDataType = "varchar";
                dataTypeModifies = String.valueOf(sourceTypedObject.getMaxLength());
                break;
            case "json":
            case "jsonb":
                localDataType = "jsonb";
                break;
            case "geometry":
            case "sdo_geometry":
            case "mdsys.sdo_geometry":
                localDataType = "geometry";
                break;
            case "number":
                localDataType = "numeric";
                if (sourceTypedObject.getPrecision() != null) {
                    dataTypeModifies = sourceTypedObject.getPrecision().toString();
                    if (sourceTypedObject.getScale() != null) {
                        dataTypeModifies += "," + sourceTypedObject.getScale();
                    }
                }
                break;
        }
        if (localDataType == null) {
            return null;
        }
        if (targetTypeProvider == null) {
            return localDataType;
        } else {
            DBSDataType dataType = targetTypeProvider.getLocalDataType(localDataType);
            if (dataType == null) {
                return null;
            }
            String targetTypeName = DBUtils.getObjectFullName(dataType, DBPEvaluationContext.DDL);
            if (dataTypeModifies != null) {
                targetTypeName += "(" + dataTypeModifies + ")";
            }
            return targetTypeName;
        }
    }

}
