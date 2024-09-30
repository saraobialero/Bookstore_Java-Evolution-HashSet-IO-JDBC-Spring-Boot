package org.evpro.bookshopV5.service;

import lombok.RequiredArgsConstructor;
import org.evpro.bookshopV5.exception.BookException;
import org.evpro.bookshopV5.exception.CartException;
import org.evpro.bookshopV5.exception.CartItemException;
import org.evpro.bookshopV5.exception.UserException;
import org.evpro.bookshopV5.model.*;
import org.evpro.bookshopV5.model.DTO.response.CartDTO;
import org.evpro.bookshopV5.model.DTO.response.ErrorResponse;
import org.evpro.bookshopV5.model.DTO.response.LoanDTO;
import org.evpro.bookshopV5.model.enums.ErrorCode;
import org.evpro.bookshopV5.model.enums.LoanStatus;
import org.evpro.bookshopV5.repository.*;
import org.evpro.bookshopV5.service.functions.CartFunctions;
import org.evpro.bookshopV5.utils.DTOConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static org.evpro.bookshopV5.utils.CodeMessages.*;
import static org.evpro.bookshopV5.utils.DTOConverter.*;

@Service
@RequiredArgsConstructor
public class CartService implements CartFunctions {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;



    @Override
    public CartDTO getCartForUser(String email) {
        userExists(email);
        Cart cart = getCartFromUser(email);
        return convertToCartDTO(cart);
    }

    @Transactional
    @Override
    public CartDTO addItemToCart(Integer userId, Integer bookId, int quantity) {
        Book book = getBook(bookId);
        Cart cart = createNewCartForUser(userId);

        Optional<CartItem> existingItem = findCartItemInCart(bookId, cart);

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(existingItem.get().getQuantity() + quantity);
            cartItemRepository.save(existingItem.get());
        } else {
            CartItem newItem = createNewCartItem(cart, book, quantity);
            cartItemRepository.save(newItem);
        }

        cart = cartRepository.save(cart);
        return convertToCartDTO(cart);
    }

    @Transactional
    @Override
    public CartDTO removeItemFromCart(Integer userId, Integer cartItemId) {
       // userExists(userId);
        CartItem cartItem = getCartItemFromCartItemId(cartItemId);
        Cart cart = getCartFromCartItem(cartItemId);

        cart.getItems().remove(cartItem);
        cartItemRepository.delete(cartItem);
        cartRepository.save(cart);

        return convertToCartDTO(cart);
    }

    @Transactional
    @Override
    public CartDTO updateCartItemQuantity(Integer cartItemId, int newQuantity) {
        CartItem cartItem = getCartItemFromCartItemId(cartItemId);

        cartItem.setQuantity(cartItem.getQuantity() + newQuantity);
        cartItemRepository.save(cartItem);

        Cart cart = getCartFromCartItem(cartItemId);

        cart.setItems(List.of(cartItem));
        cartRepository.save(cart);

        return convertToCartDTO(cart);
    }

    @Override
    public boolean clearCart(Integer userId) {
        return false;
    }

    @Override
    public LoanDTO moveCartToLoan(Integer cartId) {
        return null;
    }
/*
    @Transactional
    @Override
    public boolean clearCart(Integer userId) {
        //userExists(userId);
        Cart cart = getCartFromUserId(userId);
        List<CartItem> cartItemList = cartItemRepository.findAllByCartId(cart.getId());

        cartItemRepository.deleteAll(cartItemList);

        cart.getItems().removeAll(cartItemList);
        cartRepository.save(cart);
        return true;
    }

    @Transactional
    @Override
    public LoanDTO moveCartToLoan(Integer userId) {
        //Cart cart = getCartFromUserId(userId);
        //User user =  getUser(userId);
        List<CartItem> cartItemList = cartItemRepository.findAllByCartId(cart.getId());

        if (cartItemList.isEmpty()) {
            throw new CartException(new ErrorResponse(ErrorCode.NCCI, "no content in the list"));
        }

        Loan loan = new Loan();
        //loan.setUser(user);
        loan.setLoanDate(LocalDate.now());
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDueDate(loan.getLoanDate().plusDays(14));

        Set<LoanDetail> loanDetails = new HashSet<>();

        for (CartItem cartItem : cartItemList) {
            LoanDetail loanDetail = createNewLoanDetail(cartItem, loan);
            loanDetails.add(loanDetail);

            Book book = cartItem.getBook();
            book.setQuantity(book.getQuantity() - cartItem.getQuantity());
            bookRepository.save(book);
        }

        loan.setLoanDetails(loanDetails);
        loanRepository.save(loan);

        cart.getItems().clear();
        cartItemRepository.deleteAll(cartItemList);
        cartRepository.save(cart);

        return convertToLoanDTO(loan);
    }*/

    @Override
    public List<CartDTO> getAllCarts() {
        List<Cart> carts = getListOfCarts();
        return convertCollection(carts, DTOConverter::convertToCartDTO, ArrayList::new);
    }

    @Override
    public CartDTO getCartById(Integer cartId) {
        Cart cart = getCartFromId(cartId);
        return convertToCartDTO(cart);
    }

    @Override
    public boolean deleteCartById(Integer cartId) {
        Cart cart = getCartFromId(cartId);
        cartRepository.delete(cart);
        return true;
    }

    @Override
    public boolean deleteAllCarts() {
        List<Cart> carts = getListOfCarts();
        cartRepository.deleteAll(carts);
        return true;
    }


    private void userExists(String email) {
        if(!userRepository.existsByEmail(email)) {
            throw new UserException(
                    (new ErrorResponse(
                            ErrorCode.EUN,
                            UNF_EMAIL + email)));
        }
    }
    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException(
                        new ErrorResponse(ErrorCode.EUN,
                                UNF_EMAIL + email)));
    }
    private Book getBook(Integer bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new BookException(
                        new ErrorResponse(ErrorCode.BNF,
                                BNF)));
    }
    private Cart getCartFromCartItem(Integer cartItemId) {
        return cartRepository.findCartByCartItemId(cartItemId).orElseThrow(() -> new CartException(
                new ErrorResponse(
                        ErrorCode.CNF,
                        "Cart not found for cart item with id  " + cartItemId)));
    }
    private Cart getCartFromUser(String email) {
        return cartRepository.findCartByUserEmail(email)
                .orElseThrow(() -> new CartException(
                        new ErrorResponse(
                                ErrorCode.CNF,
                                "Cart not found for user with email " + email)));
    }
    private Cart getCartFromId(Integer cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new CartException(
                        new ErrorResponse(
                                ErrorCode.CNF,
                                "Cart not for id " + cartId)));
    }
    private Cart createNewCartForUser(Integer userId) {
        //User user = getUser(userId);
        return cartRepository.findCartByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setCreatedDate(LocalDate.now());
                    //newCart.setUser(user);
                    newCart.setItems(new ArrayList<>());
                    return newCart;
                });
    }
    private List<Cart> getListOfCarts() {
        List<Cart> carts = cartRepository.findAll();
        if (carts.isEmpty()) {
            throw new CartException(
                    new ErrorResponse(ErrorCode.NC,
                            "No carts in all system"

                    ));
        }
        return carts;
    }
    private CartItem getCartItemFromCartItemId(Integer cartItemId) {
        return cartItemRepository.findById(cartItemId).orElseThrow(() -> new CartItemException(
                new ErrorResponse(
                        ErrorCode.CINF,
                        "Cart item not found with id " + cartItemId)));
    }
    private Optional<CartItem> findCartItemInCart(Integer bookId, Cart cart) {
        return cart.getItems().stream()
                .filter(item -> item.getBook().getId().equals(bookId))
                .findFirst();
    }
    private CartItem createNewCartItem(Cart cart, Book book, int quantity) {
        CartItem cartItem = new CartItem();
        cartItem.setBook(book);
        cartItem.setCart(cart);
        cartItem.setQuantity(quantity);
        cart.getItems().add(cartItem);
        return cartItem;
    }
    private LoanDetail createNewLoanDetail(CartItem cartItem, Loan loan) {
        LoanDetail loanDetail = new LoanDetail();
        loanDetail.setBook(cartItem.getBook());
        loanDetail.setQuantity(cartItem.getQuantity());
        loanDetail.setLoan(loan);
        return  loanDetail;
    }
}
