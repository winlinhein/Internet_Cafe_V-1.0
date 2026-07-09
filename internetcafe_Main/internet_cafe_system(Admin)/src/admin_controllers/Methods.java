package admin_controllers;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

public class Methods {

    /**
     * Sends an OTP email.
     * WARNING: Hard‑coded credentials are a security risk.
     * For production, load these from an external, encrypted configuration
     * (e.g. environment variables, vault, or a secured properties file).
     */
    public static void sendEmail(String recipientEmail, String otp) {
        // 🔴 Replace with config loading or env vars in production
        String from = "koz51751@gmail.com";
        String password = "cevaiisdqzbrdckh";   // NEVER commit this!

        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject("Your OTP Code");
            message.setText("Your one‑time password is: " + otp + "\nThis code expires in 3 minutes.");

            Transport.send(message);
            System.out.println("OTP sent successfully to " + recipientEmail);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}