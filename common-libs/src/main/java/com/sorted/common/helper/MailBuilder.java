package com.sorted.common.helper;

import com.sorted.common.enums.MailTemplate;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MailBuilder {

    private List<String> to;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String content;
    private MailTemplate template;
    private List<String> attachmentUrls;

    public void setTo(List<String> to) {
        this.to = to;
    }

    public void setTo(String... to) {
        this.to = List.of(to);
    }

    public void setCc(List<String> cc) {
        this.cc = cc;
    }

    public void setCc(String... cc) {
        this.cc = List.of(cc);
    }

    public void setBcc(List<String> bcc) {
        this.bcc = bcc;
    }

    public void setBcc(String... bcc) {
        this.bcc = List.of(bcc);
    }

    public void setAttachmentUrls(String... attachmentUrls) {
        this.attachmentUrls = List.of(attachmentUrls);
    }

}
