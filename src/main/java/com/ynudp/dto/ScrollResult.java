package com.ynudp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    private List<?> list;
    // 上次最小的时间戳
    private Long minTime;
    // 偏移量
    private Integer offset;
}
