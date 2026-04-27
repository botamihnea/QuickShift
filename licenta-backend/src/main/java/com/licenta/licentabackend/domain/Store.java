package com.licenta.licentabackend.domain;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String storeName;

    private String address;

    private Double busyDaySalesThreshold;

    @OneToMany(mappedBy = "store", fetch = FetchType.LAZY)
    private List<AppUser> users;

    public Store() {}

    public Store(String storeName, String address, Double busyDaySalesThreshold) {
        this.storeName = storeName;
        this.address = address;
        this.busyDaySalesThreshold = busyDaySalesThreshold;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getBusyDaySalesThreshold() { return busyDaySalesThreshold; }
    public void setBusyDaySalesThreshold(Double busyDaySalesThreshold) { this.busyDaySalesThreshold = busyDaySalesThreshold; }

    public List<AppUser> getUsers() { return users; }
    public void setUsers(List<AppUser> users) { this.users = users; }

}
