package com.tbgbook.common.constant;

/**
 * 代码生成通用常量
 * 
 * @author ruoyi
 */
public class GenConstants
{
    /** 单表（增删改查） */
    public static final String TPL_CRUD = "crud";

    /** 树表（增删改查） */
    public static final String TPL_TREE = "tree";

    /** 主子表（增删改查） */
    public static final String TPL_SUB = "sub";

    /** 树编码字段 */
    public static final String TREE_CODE = "treeCode";

    /** 树父编码字段 */
    public static final String TREE_PARENT_CODE = "treeParentCode";

    /** 树名称字段 */
    public static final String TREE_NAME = "treeName";

    /** 上级菜单ID字段 */
    public static final String PARENT_MENU_ID = "parentMenuId";

    /** 上级菜单名称字段 */
    public static final String PARENT_MENU_NAME = "parentMenuName";

    /** 生成详情页开关 */
    public static final String GEN_VIEW = "genView";

    /** 数据库字符串类型 */
    public static final String[] COLUMNTYPE_STR = { "char", "varchar", "nvarchar", "varchar2" };

    /** 数据库文本类型 */
    public static final String[] COLUMNTYPE_TEXT = { "tinytext", "text", "mediumtext", "longtext" };

    /** 数据库纯日期类型（2026-09-22 新增）—— `date` 列不含时间，映射为 {@link #TYPE_LOCAL_DATE} */
    public static final String[] COLUMNTYPE_DATE = { "date" };

    /** 数据库日期时间类型（2026-09-22 新增）—— 映射为 {@link #TYPE_LOCAL_DATE_TIME} */
    public static final String[] COLUMNTYPE_DATETIME = { "datetime", "timestamp", "time" };

    /**
     * 数据库时间类型（含纯日期）
     * <p>
     * ⚠️ 保留仅为兼容既有引用。**新增映射逻辑请用 {@link #COLUMNTYPE_DATE} / {@link #COLUMNTYPE_DATETIME} 细分** ——
     * 这个数组把 `date` 与 `datetime` 混在一起，正是「纯日期列被迫带上时分秒」的根因。
     */
    public static final String[] COLUMNTYPE_TIME = { "datetime", "time", "date", "timestamp" };

    /** 数据库数字类型 */
    public static final String[] COLUMNTYPE_NUMBER = { "tinyint", "smallint", "mediumint", "int", "number", "integer",
            "bit", "bigint", "float", "double", "decimal" };

    /** 页面不需要编辑字段 */
    public static final String[] COLUMNNAME_NOT_EDIT = { "id", "create_by", "create_time", "del_flag" };

    /** 页面不需要显示的列表字段 */
    public static final String[] COLUMNNAME_NOT_LIST = { "id", "create_by", "create_time", "del_flag", "update_by",
            "update_time" };

    /** 页面不需要查询字段 */
    public static final String[] COLUMNNAME_NOT_QUERY = { "id", "create_by", "create_time", "del_flag", "update_by",
            "update_time", "remark" };

    /** Entity基类字段 */
    public static final String[] BASE_ENTITY = { "createBy", "createTime", "updateBy", "updateTime", "remark" };

    /** Tree基类字段 */
    public static final String[] TREE_ENTITY = { "parentName", "parentId", "orderNum", "ancestors", "children" };

    /** 文本框 */
    public static final String HTML_INPUT = "input";

    /** 文本域 */
    public static final String HTML_TEXTAREA = "textarea";

    /** 下拉框 */
    public static final String HTML_SELECT = "select";

    /** 单选框 */
    public static final String HTML_RADIO = "radio";

    /** 复选框 */
    public static final String HTML_CHECKBOX = "checkbox";

    /** 日期控件 */
    public static final String HTML_DATETIME = "datetime";

    /** 图片上传控件 */
    public static final String HTML_IMAGE_UPLOAD = "imageUpload";

    /** 文件上传控件 */
    public static final String HTML_FILE_UPLOAD = "fileUpload";

    /** 富文本控件 */
    public static final String HTML_EDITOR = "editor";

    /** 字符串类型 */
    public static final String TYPE_STRING = "String";

    /** 整型 */
    public static final String TYPE_INTEGER = "Integer";

    /** 长整型 */
    public static final String TYPE_LONG = "Long";

    /** 浮点型 */
    public static final String TYPE_DOUBLE = "Double";

    /** 高精度计算类型 */
    public static final String TYPE_BIGDECIMAL = "BigDecimal";

    /**
     * 时间类型 java.util.Date
     * <p>
     * ⚠️ 2026-09-22 起已**不再作为映射默认值**（时间列改映射为 {@link #TYPE_LOCAL_DATE_TIME} / {@link #TYPE_LOCAL_DATE}）。
     * 保留下拉选项，供第三方 API 只接受 java.util.Date 等边缘场景手工选择。
     */
    public static final String TYPE_DATE = "Date";

    /** 日期时间类型 java.time.LocalDateTime（2026-09-22 新增，datetime / timestamp / time 列的默认映射） */
    public static final String TYPE_LOCAL_DATE_TIME = "LocalDateTime";

    /** 纯日期类型 java.time.LocalDate（2026-09-22 新增，date 列的默认映射） */
    public static final String TYPE_LOCAL_DATE = "LocalDate";

    /** 模糊查询 */
    public static final String QUERY_LIKE = "LIKE";

    /** 相等查询 */
    public static final String QUERY_EQ = "EQ";

    /** 需要 */
    public static final String REQUIRE = "1";
}
