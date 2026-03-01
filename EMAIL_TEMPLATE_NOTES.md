# Email Template Notes

## Secure Pickup Reminder Template

**Template File**: `secure_pickup_reminder.html`  
**Template Enum**: `MailTemplate.SECURE_PICKUP_REMINDER`  
**Location**: Should be created in the email templates directory

### Template Variables (Pipe-separated)
The content string passed to the template contains the following variables separated by `|`:

1. **customerName** - Customer's first name
2. **orderCode** - Order code (e.g., "ORD123")
3. **itemCount** - Number of secure items being returned
4. **pickupDate** - Scheduled pickup date (LocalDate format)
5. **timeSlot** - Time slot display name (e.g., "09:00-12:00")

### Example Content String
```
John|ORD12345|3|2026-03-10|09:00-12:00
```

### Template Usage
The HTML template should use these variables to create a formatted email. Example structure:

```html
<!DOCTYPE html>
<html>
<head>
    <title>SecuRe Pickup Reminder</title>
</head>
<body>
    <h2>Hi {{customerName}},</h2>
    <h3>Reminder: Your SecuRe Pickup is Scheduled for Tomorrow!</h3>
    
    <p>This is a friendly reminder that your SecuRe return pickup for order <strong>{{orderCode}}</strong> is scheduled for tomorrow.</p>
    
    <h4>Pickup Details:</h4>
    <ul>
        <li><strong>Date:</strong> {{pickupDate}}</li>
        <li><strong>Time Slot:</strong> {{timeSlot}}</li>
        <li><strong>Items to Return:</strong> {{itemCount}} item(s)</li>
    </ul>
    
    <h4>Please ensure:</h4>
    <ul>
        <li>All items are packed and ready for pickup</li>
        <li>Items are in their original condition with tags intact</li>
        <li>Someone is available at the pickup address during the scheduled time slot</li>
    </ul>
    
    <p>Our delivery partner will arrive during the scheduled time slot to collect your items.</p>
    
    <p>If you need to reschedule, please login to your account and update the pickup details.</p>
    
    <p>Thank you for using Studeaze SecuRe!</p>
</body>
</html>
```

## Email System Pattern

### For Customer Emails
Use `MailBuilder` with template variables:

```java
MailBuilder mailBuilder = new MailBuilder();
mailBuilder.setTo(user.getEmail_id());
mailBuilder.setContent("var1|var2|var3"); // Pipe-separated variables
mailBuilder.setTemplate(MailTemplate.YOUR_TEMPLATE);
emailSenderImpl.sendEmailHtmlTemplate(mailBuilder);
```

### For Admin Error Notifications
Use `InternalMailService.sendMailOnError()`:

```java
internalMailService.sendMailOnError(
    "Error Subject", 
    "Error message details", 
    exceptionOrNull
);
```

## Current Implementation Status

✅ **SecurePickupReminderCron.java** - Fixed to use template variables  
✅ **SecureRefundStatusCron.java** - Uses `sendMailOnError` (correct for admin notifications)  
✅ **SecureReturnService.java** - Uses `sendMailOnError` (correct for admin notifications)  

⚠️ **TODO**: Create `secure_pickup_reminder.html` template file with proper variable placeholders

## Template Variable Extraction

The email system should extract variables from the pipe-separated string:
```
String[] vars = content.split("\\|");
// vars[0] = customerName
// vars[1] = orderCode
// vars[2] = itemCount
// vars[3] = pickupDate
// vars[4] = timeSlot
```

Then replace placeholders in the HTML template with these values.
