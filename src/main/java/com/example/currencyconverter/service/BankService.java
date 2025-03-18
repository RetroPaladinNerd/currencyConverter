package com.example.currencyconverter.service;

import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.repository.BankRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankService {

    private final BankRepository bankRepository;

    public Bank createBank(String name) {
        Bank bank = Bank.builder().name(name).build();
        return bankRepository.save(bank);
    }

    public Optional<Bank> getBank(Long id) {
        return bankRepository.findById(id);
    }

    public List<Bank> getAllBanks() {
        return bankRepository.findAll();
    }

    public Bank updateBank(Long id, String newName) {
        return bankRepository.findById(id)
                .map(bank -> {
                    bank.setName(newName);
                    return bankRepository.save(bank);
                })
                .orElse(null);
    }

    public boolean deleteBank(Long id) {
        if (bankRepository.existsById(id)) {
            bankRepository.deleteById(id);
            return true;
        }
        return false;
    }
}