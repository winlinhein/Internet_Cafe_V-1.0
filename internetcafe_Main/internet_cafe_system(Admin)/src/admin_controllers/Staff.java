/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package admin_controllers;

/**
 *
 * @author Hello
 */
public class Staff {
    int admin_id;
    String admin_name;
    String phone;
    String email;
    String password;

    public Staff(int admin_id, String admin_name, String phone, String email, String password) {
        this.admin_id = admin_id;
        this.admin_name = admin_name;
        this.phone = phone;
        this.email = email;
        this.password = password;
    }

    public int getAdminId() {
        return admin_id;
    }

    public String getAdmin_name() {
        return admin_name;
    }

    public void setAdmin_name(String admin_name) {
        this.admin_name = admin_name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRoleLabel() {
        return admin_id == 1 ? "Super Admin" : "Staff Admin";
    }

    public String getStatusLabel() {
        return admin_id == 1 ? "Priority Access" : "Ready for Shift";
    }

    public String getInitials() {
        if (admin_name == null || admin_name.trim().isEmpty()) {
            return "NA";
        }

        String[] parts = admin_name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }

        return initials.length() == 0 ? "NA" : initials.toString();
    }
}
