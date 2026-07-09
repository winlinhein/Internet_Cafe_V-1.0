/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package admin_controllers;

/**
 *
 * @author lenovo
 */
public class PC {
    
    private int computer_id;
    private String model;
    private String status;

    public PC(int computer_id, String model, String status) {
        this.computer_id = computer_id;
        this.model = model;
        this.status = status;
    }

    public int getComputer_id() {
        return computer_id;
    }

    public void setComputer_id(int computer_id) {
        this.computer_id = computer_id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
    
}
