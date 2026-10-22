package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReviewDocumentMapper extends BaseMapper<ReviewDocumentEntity> {
    @Select("SELECT next_issue_no FROM prd_review_document WHERE id = #{id} FOR UPDATE")
    Integer lockNextIssueNo(@Param("id") String id);

    @Update("""
            UPDATE prd_review_document
            SET next_issue_no = next_issue_no + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int incrementNextIssueNo(@Param("id") String id);
}
