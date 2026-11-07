package com.touki.demo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.touki.common.BizException;
import com.touki.common.ErrorCode;
import com.touki.common.PageResult;
import com.touki.demo.dto.DemoItemQueryDTO;
import com.touki.demo.dto.DemoItemSaveDTO;
import com.touki.demo.vo.DemoItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 示例业务。
 *
 * <p>这个类演示了一个典型 Service 的样子，写自己的业务时照这个结构改：
 * <ol>
 *   <li>查询方法拼 {@code LambdaQueryWrapper}，条件用"第一个参数为布尔"的写法，
 *       避免在代码里写一堆 {@code if (xx != null)}</li>
 *   <li>写操作加 {@code @Transactional}，并在方法开头先用 {@link #getExisting} 确认存在</li>
 *   <li>失败一律抛 {@link BizException}，不要返回 null 或 false 让 Controller 去猜</li>
 *   <li>实体转 VO 统一走私有方法，不让 Controller 碰实体</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoItemService {

    private final DemoItemMapper itemMapper;

    /**
     * 分页查询。
     *
     * <p>两个条件都是可选的，为 null 或空串时不参与 SQL。
     * 排序用更新时间倒序，让刚改过的排在最前面。
     *
     * @param query 查询条件与分页参数
     * @return 分页结果
     */
    public PageResult<DemoItemVO> page(DemoItemQueryDTO query) {
        LambdaQueryWrapper<DemoItem> wrapper = Wrappers.<DemoItem>lambdaQuery()
                // 第一个参数为条件：只有为 true 时才把后两个参数拼进 SQL
                .like(StringUtils.hasText(query.getName()), DemoItem::getName, query.getName())
                .eq(query.getStatus() != null, DemoItem::getStatus, query.getStatus())
                .orderByDesc(DemoItem::getUpdateTime);

        Page<DemoItem> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<DemoItem> result = itemMapper.selectPage(page, wrapper);

        List<DemoItemVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, result.getTotal(), query);
    }

    /**
     * 查询详情。
     *
     * @param id 主键
     * @return 详情
     * @throws BizException 数据不存在（2001）
     */
    public DemoItemVO detail(Long id) {
        return toVO(getExisting(id));
    }

    /**
     * 新增。
     *
     * @param dto 入参
     * @return 新记录主键
     * @throws BizException 名称重复（2002）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(DemoItemSaveDTO dto) {
        String name = dto.getName().trim();
        assertNameUnique(name, null);

        DemoItem item = new DemoItem();
        item.setName(name);
        // status 允许为空，这里给默认值而不是依赖数据库默认值：
        // insert 会显式写入所有非空字段，null 会被原样写进去，不会走数据库 DEFAULT
        item.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        item.setRemark(dto.getRemark());

        itemMapper.insert(item);
        log.info("新增示例数据: id={}, name={}", item.getId(), name);
        return item.getId();
    }

    /**
     * 修改。
     *
     * <p>用"新建对象 + setId"的方式而不是先查再改，避免把查出来的其他字段
     * 一起写回去（尤其是 create_time 被覆盖）。
     *
     * @param id  主键
     * @param dto 入参
     * @throws BizException 数据不存在（2001）或名称重复（2002）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DemoItemSaveDTO dto) {
        getExisting(id);

        String name = dto.getName().trim();
        // 排除自己，否则"名称没改只改了备注"会误报重名
        assertNameUnique(name, id);

        DemoItem update = new DemoItem();
        update.setId(id);
        update.setName(name);
        update.setStatus(dto.getStatus());
        update.setRemark(dto.getRemark());

        itemMapper.updateById(update);
        log.info("修改示例数据: id={}, name={}", id, name);
    }

    /**
     * 删除（物理删除）。
     *
     * <p>本项目不做逻辑删除：单人使用没有"误删要恢复"的强需求，
     * 而逻辑删除会让每个查询都得记得带 {@code deleted = 0}。
     *
     * @param id 主键
     * @throws BizException 数据不存在（2001）
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        getExisting(id);
        itemMapper.deleteById(id);
        log.info("删除示例数据: id={}", id);
    }

    /**
     * 启用 / 停用切换。
     *
     * <p>单独一个接口而不是复用 update，是因为前端列表里的开关只需要传一个值，
     * 走完整 update 要先把整条记录查出来再提交，既慢又容易覆盖别人的修改。
     *
     * @param id     主键
     * @param status 1 启用 / 0 停用
     * @throws BizException 数据不存在（2001）或状态值非法（400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态值只能是 0 或 1");
        }
        getExisting(id);

        DemoItem update = new DemoItem();
        update.setId(id);
        update.setStatus(status);
        itemMapper.updateById(update);

        log.info("示例数据 {} 状态变更为 {}", id, status == 1 ? "启用" : "停用");
    }

    // ==================== 私有方法 ====================

    /**
     * 按 ID 取实体，不存在则抛异常。
     *
     * <p>抽出来是为了让"查不到怎么办"只有一处定义，
     * 修改、删除、详情三个入口的行为保持一致。
     *
     * @param id 主键
     * @return 实体
     * @throws BizException 数据不存在（2001）
     */
    private DemoItem getExisting(Long id) {
        DemoItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(DemoError.NOT_FOUND);
        }
        return item;
    }

    /**
     * 校验名称唯一。
     *
     * @param name      名称
     * @param excludeId 修改时排除自身的 ID；新增时传 null
     * @throws BizException 重名（2002）
     */
    private void assertNameUnique(String name, Long excludeId) {
        Long count = itemMapper.selectCount(Wrappers.<DemoItem>lambdaQuery()
                .eq(DemoItem::getName, name)
                // 修改场景要排除自己，否则名称没改也会被判为重名
                .ne(excludeId != null, DemoItem::getId, excludeId));

        if (count != null && count > 0) {
            throw new BizException(DemoError.NAME_DUPLICATE);
        }
    }

    /**
     * 实体转 VO。
     *
     * @param item 实体
     * @return 出参
     */
    private DemoItemVO toVO(DemoItem item) {
        DemoItemVO vo = new DemoItemVO();
        vo.setId(item.getId());
        vo.setName(item.getName());
        vo.setStatus(item.getStatus());
        vo.setRemark(item.getRemark());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }
}
