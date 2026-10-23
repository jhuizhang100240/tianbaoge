package com.tbgbook.bookstore.member.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.tbgbook.common.annotation.Sensitive;
import com.tbgbook.common.core.domain.BaseEntity;
import com.tbgbook.common.enums.DesensitizedType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员主档对象 biz_member（页面 A-44~A-52，FR-4.4.1 / FR-4.4.2）。
 *
 * <p><b>注册以手机号为唯一标识</b>（{@code uk_phone}，§4.4 规则 1）；
 * {@code memberNo} 由服务端按 {@code M}+手机号生成，改手机号时同步重算，
 * 保证「卡号恒等于 M+当前手机号」这一不变量。
 *
 * <p><b>汇总类字段不接受客户端写入</b>（契约 `skip`）：{@code growthValue} / {@code points} /
 * {@code balancePrincipal} / {@code balanceGift} / {@code depositBalance} /
 * {@code consumeCount} / {@code consumeAmount} / {@code lastConsumeTime} / {@code status} /
 * {@code cancelTime} / {@code anonymized} —— 准数据在各自流水表，调整必须走积分 / 储值 /
 * 押金专用接口并写流水；消费汇总由结算累加；状态由冻结 / 注销动作流转。
 *
 * <p><b>脱敏（NFR-S8）</b>：手机号在列表与详情中以 {@code 138****1234} 形态返回
 * （{@link Sensitive} + {@link DesensitizedType#PHONE}，序列化时生效，不影响查询与入库）。
 *
 * <p><b>无 {@code del_flag}</b>：本表不是基础数据表 —— 业务上「离场」走
 * {@code POST /member/member/{memberId}/cancel} 注销（匿名化 + 保留流水关联）；
 * {@code DELETE} 仅用于删除**无任何业务痕迹**的误录会员，有痕迹一律 422。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_member")
public class Member extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    @TableId(value = "member_id", type = IdType.AUTO)
    private Long memberId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 会员卡号（M+手机号，服务端生成） */
    @TableField("member_no")
    private String memberNo;

    /** 手机号（唯一标识；列表与详情脱敏返回） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Sensitive(desensitizedType = DesensitizedType.PHONE)
    @TableField("phone")
    private String phone;

    /** 昵称 */
    @Size(max = 50, message = "昵称不能超过 50 个字符")
    @TableField("nickname")
    private String nickname;

    /** 真实姓名（学生认证后回填，注销时匿名化） */
    @Size(max = 50, message = "真实姓名不能超过 50 个字符")
    @TableField("real_name")
    private String realName;

    /** 头像地址 */
    @Size(max = 500, message = "头像地址不能超过 500 个字符")
    @TableField("avatar")
    private String avatar;

    /** 性别（0未知 1男 2女） */
    @Min(value = 0, message = "性别取值必须是 0/1/2")
    @Max(value = 2, message = "性别取值必须是 0/1/2")
    @TableField("gender")
    private Integer gender;

    /** 生日 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("birthday")
    private LocalDate birthday;

    /** 当前等级ID */
    @TableField("level_id")
    private Long levelId;

    /** 当前等级名称（冗余，随等级变更同步） */
    @TableField("level_name")
    private String levelName;

    /** 成长值（只增不减，用于等级判定；准数据见 biz_member_growth_log） */
    @TableField("growth_value")
    private Integer growthValue;

    /** 可用积分（冗余汇总，准数据见 biz_point_batch / biz_point_log） */
    @TableField("points")
    private Integer points;

    /** 储值本金（冗余汇总，准数据见 biz_member_balance） */
    @TableField("balance_principal")
    private BigDecimal balancePrincipal;

    /** 储值赠送金（冗余汇总） */
    @TableField("balance_gift")
    private BigDecimal balanceGift;

    /** 已缴押金余额（冗余汇总，准数据见 biz_deposit_log） */
    @TableField("deposit_balance")
    private BigDecimal depositBalance;

    /** 学生认证状态（0未认证 1审核中 2已认证 3已拒绝） */
    @TableField("student_status")
    private Integer studentStatus;

    /** 累计消费次数（结算累加） */
    @TableField("consume_count")
    private Integer consumeCount;

    /** 累计消费金额（结算累加） */
    @TableField("consume_amount")
    private BigDecimal consumeAmount;

    /** 最近消费时间（结算刷新） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_consume_time")
    private LocalDateTime lastConsumeTime;

    /** 注册时间（服务端写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("register_time")
    private LocalDateTime registerTime;

    /** 注册来源（1微信小程序 2门店开卡） */
    @Min(value = 1, message = "注册来源取值必须是 1 或 2")
    @Max(value = 2, message = "注册来源取值必须是 1 或 2")
    @TableField("register_source")
    private Integer registerSource;

    /** 状态（1正常 2冻结 3黑名单 4已注销；由动作端点流转，不接受直接写入） */
    @TableField("status")
    private Integer status;

    /** 注销时间（注销动作写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("cancel_time")
    private LocalDateTime cancelTime;

    /** 是否已匿名化（0否 1是；注销动作写入） */
    @TableField("anonymized")
    private Integer anonymized;
}
