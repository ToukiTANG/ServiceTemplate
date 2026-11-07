package com.touki.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.touki.common.BizException;
import com.touki.common.ErrorCode;
import com.touki.common.PageResult;
import com.touki.tool.dto.ToolQueryDTO;
import com.touki.tool.dto.ToolSaveDTO;
import com.touki.tool.vo.PortalToolVO;
import com.touki.tool.vo.ToolVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工具业务。
 *
 * <p>两类使用方，注意区分：
 * <ul>
 *   <li>{@link #page} 等方法供管理端使用，返回全部数据（含下架）</li>
 *   <li>{@link #listVisible} 供展示端使用，只返回上架数据</li>
 * </ul>
 * 这两条路径必须分开实现，不要通过参数切换——一旦共用一个方法，
 * 将来某个入口忘了传"只要上架"的标记，下架的工具就会出现在展示页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    /** 分类为空时使用的默认值，与建表脚本里的 DEFAULT 保持一致 */
    private static final String DEFAULT_CATEGORY = "默认";

    private final ToolMapper toolMapper;

    /**
     * 管理端分页查询。
     *
     * <p>三个条件都是可选的，为 null 或空串时不参与 SQL。
     * 排序用更新时间倒序，这样刚改过的排在最前面，符合"管理"场景的使用习惯。
     *
     * @param query 查询条件与分页参数
     * @return 分页结果
     */
    public PageResult<ToolVO> page(ToolQueryDTO query) {
        LambdaQueryWrapper<Tool> wrapper = Wrappers.<Tool>lambdaQuery()
                // 第一个参数为条件：只有为 true 时才把后两个参数拼进 SQL
                .like(StringUtils.hasText(query.getName()), Tool::getName, query.getName())
                .eq(StringUtils.hasText(query.getCategory()), Tool::getCategory, query.getCategory())
                .eq(query.getStatus() != null, Tool::getStatus, query.getStatus())
                .orderByDesc(Tool::getUpdateTime);

        Page<Tool> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<Tool> result = toolMapper.selectPage(page, wrapper);

        List<ToolVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, result.getTotal(), query);
    }

    /**
     * 查询单个工具详情。
     *
     * @param id 工具 ID
     * @return 工具详情
     * @throws BizException 工具不存在（2001）
     */
    public ToolVO detail(Long id) {
        return toVO(getExisting(id));
    }

    /**
     * 新增工具。
     *
     * @param dto 工具信息
     * @return 新工具 ID
     * @throws BizException 同分类下重名（2002）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(ToolSaveDTO dto) {
        String name = dto.getName().trim();
        String category = normalizeCategory(dto.getCategory());

        assertNameUnique(name, category, null);

        Tool tool = new Tool();
        tool.setName(name);
        tool.setCategory(category);
        tool.setDescription(dto.getDescription());
        tool.setIcon(dto.getIcon());
        tool.setLink(dto.getLink().trim());
        // sort 与 status 允许为空，这里给默认值而不是依赖数据库默认值：
        // 用的是 insert（显式插入所有非空字段），null 会被原样写进去，不会走数据库 DEFAULT
        tool.setSort(dto.getSort() == null ? 0 : dto.getSort());
        tool.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());

        toolMapper.insert(tool);
        log.info("新增工具: id={}, name={}, category={}", tool.getId(), name, category);
        return tool.getId();
    }

    /**
     * 修改工具。
     *
     * <p>只更新 DTO 里有的字段，用"新建对象 + setId"的方式而不是先查再改，
     * 避免把查出来的其他字段一起写回去（尤其是 create_time 被覆盖）。
     *
     * @param id  工具 ID
     * @param dto 工具信息
     * @throws BizException 工具不存在（2001）或同分类下重名（2002）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ToolSaveDTO dto) {
        getExisting(id);

        String name = dto.getName().trim();
        String category = normalizeCategory(dto.getCategory());

        // 排除自己，否则"名称没改只改了简介"会误报重名
        assertNameUnique(name, category, id);

        Tool update = new Tool();
        update.setId(id);
        update.setName(name);
        update.setCategory(category);
        update.setDescription(dto.getDescription());
        update.setIcon(dto.getIcon());
        update.setLink(dto.getLink().trim());
        update.setSort(dto.getSort());
        update.setStatus(dto.getStatus());

        toolMapper.updateById(update);
        log.info("修改工具: id={}, name={}", id, name);
    }

    /**
     * 删除工具（物理删除）。
     *
     * <p>本项目不做逻辑删除：单人使用没有"误删要恢复"的强需求，
     * 而逻辑删除会带来唯一索引冲突、所有查询都要带 deleted 条件等一系列麻烦。
     *
     * @param id 工具 ID
     * @throws BizException 工具不存在（2001）
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        getExisting(id);
        toolMapper.deleteById(id);
        log.info("删除工具: id={}", id);
    }

    /**
     * 上架 / 下架切换。
     *
     * <p>单独一个接口而不是复用 update，是因为前端列表里的开关只需要传一个值，
     * 走完整 update 要先把整条记录查出来再提交，既慢又容易覆盖别人的修改。
     *
     * @param id     工具 ID
     * @param status 1 上架 / 0 下架
     * @throws BizException 工具不存在（2001）或状态值非法（400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态值只能是 0 或 1");
        }
        getExisting(id);

        Tool update = new Tool();
        update.setId(id);
        update.setStatus(status);
        toolMapper.updateById(update);

        log.info("工具 {} 状态变更为 {}", id, status == 1 ? "上架" : "下架");
    }

    /**
     * 展示端查询：所有上架工具。
     *
     * <p>不分页——工具总量通常只有几十条，一次全返回，前端按分类分组渲染即可。
     * 如果将来真的超过几百条，再改成分页或按分类分批加载。
     *
     * <p>排序规则：先按分类名升序，再按 sort 升序。
     * 前端直接按返回顺序渲染就能得到稳定的分组顺序，不需要自己排序。
     *
     * @return 上架工具列表
     */
    public List<PortalToolVO> listVisible() {
        List<Tool> tools = toolMapper.selectList(Wrappers.<Tool>lambdaQuery()
                .eq(Tool::getStatus, 1)
                .orderByAsc(Tool::getCategory)
                .orderByAsc(Tool::getSort));

        return tools.stream().map(this::toPortalVO).toList();
    }

    // ==================== 私有方法 ====================

    /**
     * 按 ID 取工具，不存在则抛业务异常。
     *
     * <p>抽出来是为了让"查不到怎么办"只有一处定义，
     * 修改、删除、详情三个入口的行为保持一致。
     *
     * @param id 工具 ID
     * @return 工具实体
     * @throws BizException 工具不存在（2001）
     */
    private Tool getExisting(Long id) {
        Tool tool = toolMapper.selectById(id);
        if (tool == null) {
            throw new BizException(ToolError.NOT_FOUND);
        }
        return tool;
    }

    /**
     * 校验同一分类下名称唯一。
     *
     * @param name      名称
     * @param category  分类
     * @param excludeId 修改时排除自身的 ID；新增时传 null
     * @throws BizException 重名（2002）
     */
    private void assertNameUnique(String name, String category, Long excludeId) {
        Long count = toolMapper.selectCount(Wrappers.<Tool>lambdaQuery()
                .eq(Tool::getName, name)
                .eq(Tool::getCategory, category)
                // 修改场景要排除自己，否则名称没改也会被判为重名
                .ne(excludeId != null, Tool::getId, excludeId));

        if (count != null && count > 0) {
            throw new BizException(ToolError.NAME_DUPLICATE);
        }
    }

    /**
     * 规范化分类名：去空格，空值回落到默认分类。
     *
     * <p>必须做这一步：分类是字符串而非外键，如果允许空串和 null 并存，
     * 前端分组时会出现"默认"和""两个组，展示效果很怪。
     *
     * @param category 原始分类
     * @return 规范化后的分类
     */
    private String normalizeCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : DEFAULT_CATEGORY;
    }

    /**
     * 实体转管理端 VO。
     *
     * @param tool 实体
     * @return 出参
     */
    private ToolVO toVO(Tool tool) {
        ToolVO vo = new ToolVO();
        vo.setId(tool.getId());
        vo.setName(tool.getName());
        vo.setDescription(tool.getDescription());
        vo.setIcon(tool.getIcon());
        vo.setLink(tool.getLink());
        vo.setCategory(tool.getCategory());
        vo.setSort(tool.getSort());
        vo.setStatus(tool.getStatus());
        vo.setCreateTime(tool.getCreateTime());
        vo.setUpdateTime(tool.getUpdateTime());
        return vo;
    }

    /**
     * 实体转展示端 VO。
     *
     * @param tool 实体
     * @return 出参
     */
    private PortalToolVO toPortalVO(Tool tool) {
        PortalToolVO vo = new PortalToolVO();
        vo.setId(tool.getId());
        vo.setName(tool.getName());
        vo.setDescription(tool.getDescription());
        vo.setIcon(tool.getIcon());
        vo.setLink(tool.getLink());
        vo.setCategory(tool.getCategory());
        vo.setSort(tool.getSort());
        return vo;
    }
}
