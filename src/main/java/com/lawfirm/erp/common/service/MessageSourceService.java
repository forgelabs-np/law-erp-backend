package com.lawfirm.erp.common.service;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MessageSourceService {

    private final MessageSource messageSource;

    public MessageSourceService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String getMessage(String code, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, args, code, locale);
    }
}