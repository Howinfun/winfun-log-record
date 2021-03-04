package com.howinfun.log.record.sdk.aop;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.howinfun.log.record.sdk.contants.LogRecordContants;
import com.howinfun.log.record.sdk.entity.LogRecord;
import com.howinfun.log.record.sdk.service.LogRecordSDKService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LogRecord Aspect
 * @author winfun
 * @date 2021/2/25 4:52 下午
 **/
@Slf4j
@Aspect
public class LogRecordAspect {

    private static final Pattern PATTERN = Pattern.compile("(?<=\\{\\{)(.+?)(?=}})");

    private final ExpressionParser parser = new SpelExpressionParser();
    private final LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();

    @Autowired
    ApplicationContext applicationContext;
    @Resource
    private LogRecordSDKService logRecordSDKService;

    @Around("@annotation(logRecordAnno))")
    public Object around(final ProceedingJoinPoint point, final LogRecordAnno logRecordAnno) throws Throwable {

        final Method method = this.getMethod(point);
        final Object[] args = point.getArgs();
        // 日志记录
        final LogRecord logRecord = new LogRecord();
        // 日志类型
        final String logType = logRecordAnno.logType();
        // sql类型
        final String sqlType = logRecordAnno.sqlType();
        // 业务名称
        final String businessName = logRecordAnno.businessName();
        final EvaluationContext context = this.bindParam(method, args);
        // 获取操作者
        final String operator = this.getOperator(logRecordAnno.operator(), context);
        logRecord.setLogType(logType);
        logRecord.setSqlType(sqlType);
        logRecord.setBusinessName(businessName);
        logRecord.setOperator(operator);
        Object proceedResult = null;
        // 记录实体记录
        if (LogRecordContants.LOG_TYPE_RECORD.equals(logType)){
            final Class mapperClass = logRecordAnno.mapperName();
            if (mapperClass.isAssignableFrom(BaseMapper.class)){
                throw new RuntimeException("mapperClass 属性传入 Class 不是 BaseMapper 的子类");
            }
            final BaseMapper mapper = (BaseMapper) this.applicationContext.getBean(mapperClass);
            //根据spel表达式获取id
            final String id = (String) this.getId(logRecordAnno.id(), context);
            final Object beforeRecord;
            final Object afterRecord;
            switch (sqlType){
                // 新增
                case LogRecordContants.SQL_TYPE_INSERT:
                    proceedResult = point.proceed();
                    final Object result = mapper.selectById(id);
                    logRecord.setBeforeRecord("");
                    logRecord.setAfterRecord(JSON.toJSONString(result));
                    break;
                // 更新
                case LogRecordContants.SQL_TYPE_UPDATE:
                    beforeRecord = mapper.selectById(id);
                    proceedResult = point.proceed();
                    afterRecord = mapper.selectById(id);
                    logRecord.setBeforeRecord(JSON.toJSONString(beforeRecord));
                    logRecord.setAfterRecord(JSON.toJSONString(afterRecord));
                    break;
                // 删除
                case LogRecordContants.SQL_TYPE_DELETE:
                    beforeRecord = mapper.selectById(id);
                    proceedResult = point.proceed();
                    logRecord.setBeforeRecord(JSON.toJSONString(beforeRecord));
                    logRecord.setAfterRecord("");
                    break;
                default:
                    break;
            }
        // 记录信息
        }else if (LogRecordContants.LOG_TYPE_MESSAGE.equals(logType)){
            try {
                proceedResult = point.proceed();
                String successMsg = logRecordAnno.successMsg();
                // 对成功信息做表达式提取
                final Matcher successMatcher = PATTERN.matcher(successMsg);
                while(successMatcher.find()){
                    String temp = successMatcher.group();
                    final Expression tempExpression = this.parser.parseExpression(temp);
                    final String result = (String) tempExpression.getValue(context);
                    temp = "{{"+temp+"}}";
                    successMsg = successMsg.replace(temp,result);
                }
                logRecord.setSuccessMsg(successMsg);
            }catch (final Exception e){
                String errorMsg = logRecordAnno.errorMsg();
                final String exceptionMsg = e.getMessage();
                errorMsg = errorMsg.replace(LogRecordContants.ERROR_MSG_PATTERN,exceptionMsg);
                logRecord.setSuccessMsg(errorMsg);
                // 插入记录
                logRecord.setCreateTime(LocalDateTime.now());
                this.logRecordSDKService.insertLogRecord(logRecord);
                // 回抛异常
                throw new Exception(errorMsg);
            }
        }
        // 插入记录
        logRecord.setCreateTime(LocalDateTime.now());
        this.logRecordSDKService.insertLogRecord(logRecord);
        return proceedResult;
    }

    /**
     * 获取当前执行的方法
     *
     * @param pjp
     * @return
     * @throws NoSuchMethodException
     */
    private Method getMethod(final ProceedingJoinPoint pjp) throws NoSuchMethodException {
        final MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        final Method method = methodSignature.getMethod();
        final Method targetMethod = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
        return targetMethod;
    }

    /**
     * 根据表达式获取ID
     * @param expressionStr
     * @param context
     * @return
     */
    private Object getId(final String expressionStr, final EvaluationContext context){
        final Expression idExpression = this.parser.parseExpression(expressionStr);
        return idExpression.getValue(context);
    }

    /**
     * 获取操作者
     * @param expressionStr
     * @param context
     * @return
     */
    private String getOperator(final String expressionStr, final EvaluationContext context){
        try {
            if (expressionStr.startsWith("#")){
                final Expression idExpression = this.parser.parseExpression(expressionStr);
                return (String) idExpression.getValue(context);
            }else {
                return expressionStr;
            }
        }catch (final Exception e){
            log.error("Log-Record-SDK 获取操作者失败！，错误信息：{}",e.getMessage());
            return "default";
        }
    }

    /**
     * 将方法的参数名和参数值绑定
     *
     * @param method 方法，根据方法获取参数名
     * @param args   方法的参数值
     * @return
     */
    private EvaluationContext bindParam(final Method method, final Object[] args) {
        //获取方法的参数名
        final String[] params = this.discoverer.getParameterNames(method);

        //将参数名与参数值对应起来
        final EvaluationContext context = new StandardEvaluationContext();
        for (int len = 0; len < params.length; len++) {
            context.setVariable(params[len], args[len]);
        }
        return context;
    }

    public static void main(final String[] args) {
        String str = "成功新增用户{{#user.name}}";
        str = str.replace("{{#user.name}}","luff");
        System.out.println(str);
    }
}
