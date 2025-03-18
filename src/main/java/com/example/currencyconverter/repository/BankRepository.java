package com.example.currencyconverter.repository;

import com.example.currencyconverter.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, Long> {}