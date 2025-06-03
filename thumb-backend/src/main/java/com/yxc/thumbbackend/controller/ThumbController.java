package com.yxc.thumbbackend.controller;

import com.yxc.thumbbackend.common.BaseResponse;
import com.yxc.thumbbackend.common.ResultUtils;
import com.yxc.thumbbackend.exception.BusinessException;
import com.yxc.thumbbackend.exception.ErrorCode;
import com.yxc.thumbbackend.model.dto.DoThumbRequest;
import com.yxc.thumbbackend.service.ThumbService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("thumb")
public class ThumbController {  
    @Resource
    private ThumbService thumbService;

    // 统计业务关键指标
    private final Counter successCounter;
    private final Counter failureCounter;

    public ThumbController(MeterRegistry registry) {
        this.successCounter = Counter.builder("thumb.success.count")
                .description("Total successful thumb")
                .register(registry);
        this.failureCounter = Counter.builder("thumb.failure.count")
                .description("Total failed thumb")
                .register(registry);
    }


    /**
     * 点赞
     *
     * @param doThumbRequest
     * @param request
     * @return
     */
    @PostMapping("/do")
    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        try {
            Boolean success = thumbService.doThumb(doThumbRequest, request);

            if (success) {
                successCounter.increment();
            } else {
                failureCounter.increment();
            }

            return ResultUtils.success(success);
        } catch (Exception e) {
            failureCounter.increment();

            throw e;
        }
    }

    /**
     * 取消点赞
     *
     * @param doThumbRequest
     * @param request
     * @return
     */
    @PostMapping("/undo")
    public BaseResponse<Boolean> undoThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean success = thumbService.undoThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }
}
