package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewMemberMapper extends BaseMapper<ReviewMemberEntity> {
    @Select("SELECT * FROM prd_review_member WHERE task_id = #{taskId} FOR UPDATE")
    List<ReviewMemberEntity> lockByTaskId(@Param("taskId") String taskId);

    @Select("""
            SELECT * FROM prd_review_member
            WHERE task_id = #{taskId} AND user_id = #{userId}
            FOR UPDATE
            """)
    ReviewMemberEntity lockByTaskAndUser(@Param("taskId") String taskId, @Param("userId") String userId);
}
