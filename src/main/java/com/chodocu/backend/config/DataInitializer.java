package com.chodocu.backend.config;

import com.chodocu.backend.entity.*;
import com.chodocu.backend.enums.OrderStatus;
import com.chodocu.backend.enums.ProductCondition;
import com.chodocu.backend.enums.ProductStatus;
import com.chodocu.backend.enums.Role;
import com.chodocu.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UniversityRepository universityRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRequestRepository orderRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WishlistRepository wishlistRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            log.info("Database đã có dữ liệu mẫu. Bỏ qua bước khởi tạo.");
            return;
        }

        log.info("Đang khởi tạo dữ liệu mẫu Chợ Đồ Cũ Sinh Viên (PassUni) trên Supabase...");

        // 1. Tạo danh sách Trường Đại học & Khu KTX
        University ktxKhuB = universityRepository.save(University.builder()
                .name("KTX Khu B - ĐHQG TP.HCM")
                .shortName("KTX Khu B")
                .emailDomain("vnuhcm.edu.vn")
                .address("Khu phố 6, P. Linh Trung, TP. Thủ Đức")
                .safeMeetupSpots("Sảnh Tầng 1 Tòa BA1, Căng tin B4, Sảnh Nhà Văn Hóa Sinh Viên")
                .build());

        University dhBachKhoa = universityRepository.save(University.builder()
                .name("ĐH Bách Khoa - ĐHQG TP.HCM")
                .shortName("ĐH Bách Khoa")
                .emailDomain("hcmut.edu.vn")
                .address("268 Lý Thường Kiệt / Làng ĐH Thủ Đức")
                .safeMeetupSpots("Thư viện H1, Sảnh B1, Cổng chính Tô Hiến Thành")
                .build());

        University dhTuNhien = universityRepository.save(University.builder()
                .name("ĐH Khoa Học Tự Nhiên - ĐHQG")
                .shortName("ĐH Tự Nhiên")
                .emailDomain("hcmus.edu.vn")
                .address("Linh Trung, TP. Thủ Đức")
                .safeMeetupSpots("Sảnh nhà điều hành, Thư viện Tự Nhiên")
                .build());

        University dhCNTT = universityRepository.save(University.builder()
                .name("ĐH Công Nghệ Thông Tin (UIT)")
                .shortName("ĐH CNTT - UIT")
                .emailDomain("uit.edu.vn")
                .address("Khu phố 6, P. Linh Trung, TP. Thủ Đức")
                .safeMeetupSpots("Sảnh tòa A, Căng tin UIT, Thư viện")
                .build());

        University dhKinhTeLuat = universityRepository.save(University.builder()
                .name("ĐH Kinh Tế - Luật (UEL)")
                .shortName("ĐH Kinh Tế - Luật")
                .emailDomain("uel.edu.vn")
                .address("Quốc lộ 1A, Khu phố 3, P. Linh Xuân, Thủ Đức")
                .safeMeetupSpots("Sảnh chính UEL, Khu tự học sinh viên")
                .build());

        // 2. Tạo Danh mục sản phẩm
        Category catBooks = categoryRepository.save(Category.builder()
                .name("Giáo trình & Sách")
                .slug("giao-trinh-sach")
                .icon("menu_book")
                .description("Giáo trình đại cương, sách chuyên ngành, tài liệu ôn thi")
                .build());

        Category catElectronics = categoryRepository.save(Category.builder()
                .name("Đồ điện & Quạt")
                .slug("do-dien-quat")
                .icon("mode_fan")
                .description("Quạt đứng, nồi lẩu mini, ấm siêu tốc, đèn bàn học")
                .build());

        Category catFurniture = categoryRepository.save(Category.builder()
                .name("Đồ dùng & Bàn")
                .slug("do-dung-ban")
                .icon("desk")
                .description("Bàn học gấp gọn, ghế xoay, kệ sách, tủ nhựa KTX")
                .build());

        Category catFashion = categoryRepository.save(Category.builder()
                .name("Quần áo & Giày")
                .slug("quan-ao-giay")
                .icon("checkroom")
                .description("Đồng phục thể dục, áo khoác, giày thể thao, balo laptop")
                .build());

        Category catVehicle = categoryRepository.save(Category.builder()
                .name("Xe đạp & Xe máy")
                .slug("xe-dap-xe-may")
                .icon("pedal_bike")
                .description("Xe đạp đi học trong làng ĐH, mũ bảo hiểm, phụ kiện")
                .build());

        Category catDecor = categoryRepository.save(Category.builder()
                .name("Decor phòng trọ")
                .slug("decor-phong-tro")
                .icon("potted_plant")
                .description("Cây để bàn, thảm trải sàn, gương soi, giá đỡ sách")
                .build());

        // 3. Tạo Tài khoản Sinh viên & Admin
        User admin = userRepository.save(User.builder()
                .email("admin@chodocu.edu.vn")
                .password(passwordEncoder.encode("admin123"))
                .fullName("Ban Quản Trị PassUni")
                .studentId("ADMIN01")
                .phone("0901234567")
                .university(ktxKhuB)
                .dormArea("Trung tâm Quản lý KTX ĐHQG")
                .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80")
                .role(Role.ADMIN)
                .verifiedStudent(true)
                .active(true)
                .rating(5.0)
                .completedDeals(99)
                .build());

        User minhThu = userRepository.save(User.builder()
                .email("minhthu@student.vnuhcm.edu.vn")
                .password(passwordEncoder.encode("123456"))
                .fullName("Nguyễn Minh Thư")
                .studentId("21120501")
                .phone("0912345678")
                .university(ktxKhuB)
                .dormArea("Tòa BA1 - KTX Khu B")
                .avatarUrl("https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200&auto=format&fit=crop&q=80")
                .role(Role.STUDENT)
                .verifiedStudent(true)
                .active(true)
                .rating(4.9)
                .completedDeals(12)
                .build());

        User hoangYen = userRepository.save(User.builder()
                .email("hoangyen@hcmut.edu.vn")
                .password(passwordEncoder.encode("123456"))
                .fullName("Lê Hoàng Yến")
                .studentId("2014512")
                .phone("0987654321")
                .university(dhBachKhoa)
                .dormArea("Tòa BA1 - KTX Khu B")
                .avatarUrl("https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=200&auto=format&fit=crop&q=80")
                .role(Role.STUDENT)
                .verifiedStudent(true)
                .active(true)
                .rating(4.9)
                .completedDeals(24)
                .build());

        User hoangPhuc = userRepository.save(User.builder()
                .email("hoangphuc@uit.edu.vn")
                .password(passwordEncoder.encode("123456"))
                .fullName("Trần Hoàng Phúc")
                .studentId("21520399")
                .phone("0938112233")
                .university(dhCNTT)
                .dormArea("Tòa C2 - KTX Khu B")
                .avatarUrl("https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=200&auto=format&fit=crop&q=80")
                .role(Role.STUDENT)
                .verifiedStudent(true)
                .active(true)
                .rating(4.8)
                .completedDeals(9)
                .build());

        User ngocAnh = userRepository.save(User.builder()
                .email("ngocanh@hcmus.edu.vn")
                .password(passwordEncoder.encode("123456"))
                .fullName("Phạm Ngọc Ánh")
                .studentId("22110088")
                .phone("0977889900")
                .university(dhTuNhien)
                .dormArea("Tòa D5 - KTX Khu A")
                .avatarUrl("https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=200&auto=format&fit=crop&q=80")
                .role(Role.STUDENT)
                .verifiedStudent(true)
                .active(true)
                .rating(5.0)
                .completedDeals(15)
                .build());

        // 4. Tạo Sản phẩm mẫu chuẩn sinh viên
        Product p1 = productRepository.save(Product.builder()
                .title("Bộ Bàn Ghế Học Gấp Gọn Kèm Giá Đỡ Sách Thông Minh")
                .description("Dùng được đúng 1 học kỳ ôn thi đại cương, giữ rất kỹ nên hầu như không trầy xước. Khung chân kim loại sơn tĩnh điện chịu lực tốt không rung lắc. Giá đỡ sách tích hợp đọc giáo trình dày cộp vẫn vô tư. Gấp gọn siêu nhanh chỉ 5cm nhét gầm giường KTX.")
                .price(new BigDecimal("110000"))
                .originalPrice(new BigDecimal("280000"))
                .isFree(false)
                .isUrgent(true)
                .itemCondition(ProductCondition.LIKE_NEW_95)
                .conditionNote("Mới 95% • Dọn phòng về quê")
                .passReason("Dọn phòng KTX / Chuyển trọ")
                .pickupSpot("Căng tin Tòa BA1 hoặc Sảnh Nhà Văn Hóa Sinh Viên")
                .status(ProductStatus.APPROVED)
                .viewCount(142)
                .favoriteCount(19)
                .category(catFurniture)
                .university(ktxKhuB)
                .seller(hoangYen)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?w=800&auto=format&fit=crop&q=80",
                        "https://images.unsplash.com/photo-1524758631624-e2822e304c36?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        Product p2 = productRepository.save(Product.builder()
                .title("Quạt đứng Senko lồng sắt 3 tốc độ chạy êm")
                .description("Quạt Senko chính hãng mua đầu năm tại Điện Máy Xanh, lồng sắt chắc chắn, motor đồng chạy cực êm không kêu rè rè khi ngủ trưa ở KTX. Mình tốt nghiệp ra trường nên pass nhanh trong chiều nay.")
                .price(new BigDecimal("120000"))
                .originalPrice(new BigDecimal("290000"))
                .isFree(false)
                .isUrgent(true)
                .itemCondition(ProductCondition.GOOD_85)
                .conditionNote("Mới 90% • Pass gấp trong chiều")
                .passReason("Ra trường / Tốt nghiệp")
                .pickupSpot("Sảnh Tòa H1 - ĐH Bách Khoa")
                .status(ProductStatus.APPROVED)
                .viewCount(98)
                .favoriteCount(14)
                .category(catElectronics)
                .university(dhBachKhoa)
                .seller(hoangYen)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1618941716939-553df3c6c278?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        Product p3 = productRepository.save(Product.builder()
                .title("Combo Giáo trình Giải tích 1 & 2 + Đại số Tuyến tính (Tặng đề thi)")
                .description("Trọn bộ giáo trình Giải tích 1, Giải tích 2 và Bài tập Đại số tuyến tính chuẩn ĐHQG. Sách bọc bìa kỹ, có highlight nhẹ các công thức trọng tâm hay ra thi giữa kỳ & cuối kỳ. Tặng kèm tập đề thi mẫu 3 năm gần nhất!")
                .price(new BigDecimal("35000"))
                .originalPrice(new BigDecimal("95000"))
                .isFree(false)
                .isUrgent(true)
                .itemCondition(ProductCondition.LIKE_NEW_95)
                .conditionNote("Sách đẹp • Tặng kèm đề thi")
                .passReason("Học xong môn / Qua môn")
                .pickupSpot("Thư viện Trung tâm ĐH Tự Nhiên")
                .status(ProductStatus.APPROVED)
                .viewCount(215)
                .favoriteCount(32)
                .category(catBooks)
                .university(dhTuNhien)
                .seller(ngocAnh)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800&auto=format&fit=crop&q=80",
                        "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        Product p4 = productRepository.save(Product.builder()
                .title("Nồi lẩu mini Bear 1.2L đa năng kèm xửng hấp inox")
                .description("Nồi lẩu điện mini Bear màu xanh mint siêu xinh, dung tích 1.2L vừa vặn nấu mì, luộc trứng, hấp bánh bao ăn đêm ôn thi. Lòng nồi chống dính vân đá chưa một vết xước.")
                .price(new BigDecimal("110000"))
                .originalPrice(new BigDecimal("250000"))
                .isFree(false)
                .isUrgent(false)
                .itemCondition(ProductCondition.LIKE_NEW_95)
                .conditionNote("Mới 98% • Đủ hộp & dây nguồn")
                .passReason("Lên đời đồ mới")
                .pickupSpot("Sảnh Tòa C2 - KTX Khu B")
                .status(ProductStatus.APPROVED)
                .viewCount(87)
                .favoriteCount(11)
                .category(catElectronics)
                .university(ktxKhuB)
                .seller(hoangPhuc)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1584269600464-37b1b58a9fe7?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        Product p5 = productRepository.save(Product.builder()
                .title("Đèn học chống cận Điện Quang LED 3 chế độ ánh sáng")
                .description("Đèn bàn học cảm ứng 3 mức sáng Trắng - Vàng - Trung tính, cổ ngỗng uốn dẻo 360 độ, kẹp trực tiếp vào thành giường tầng KTX hoặc để bàn đều được. Bảo vệ mắt cực tốt khi code đêm.")
                .price(new BigDecimal("60000"))
                .originalPrice(new BigDecimal("150000"))
                .isFree(false)
                .isUrgent(false)
                .itemCondition(ProductCondition.LIKE_NEW_95)
                .conditionNote("Like new • Ánh sáng dịu mắt")
                .passReason("Dọn phòng KTX / Chuyển trọ")
                .pickupSpot("Sảnh Tòa D5 - KTX Khu A")
                .status(ProductStatus.APPROVED)
                .viewCount(64)
                .favoriteCount(8)
                .category(catElectronics)
                .university(dhTuNhien)
                .seller(ngocAnh)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1507473885765-e6ed057f782c?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        Product p6 = productRepository.save(Product.builder()
                .title("Giày Biti's Hunter X trắng size 40 mang đúng 2 lần")
                .description("Mua đi học Quân sự nhưng hơi kích chân nên chỉ mới mang đúng 2 buổi lễ. Đế LiteFlex còn nguyên gai trắng tinh, đã giặt sạch thơm tho, full box Biti's.")
                .price(new BigDecimal("195000"))
                .originalPrice(new BigDecimal("520000"))
                .isFree(false)
                .isUrgent(false)
                .itemCondition(ProductCondition.LIKE_NEW_95)
                .conditionNote("Mới 95% • Size 40")
                .passReason("Ít dùng để phí")
                .pickupSpot("Cổng chính ĐH CNTT (UIT)")
                .status(ProductStatus.APPROVED)
                .viewCount(112)
                .favoriteCount(16)
                .category(catFashion)
                .university(dhCNTT)
                .seller(hoangPhuc)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        Product p7 = productRepository.save(Product.builder()
                .title("Kệ nhựa đầu giường 3 tầng có bánh xe đẩy KTX")
                .description("Kệ nhựa PP dày dặn màu kem Hàn Quốc, 3 tầng đựng mỹ phẩm, sách vở, đồ ăn vặt cực gọn. Có 4 bánh xe xoay 360 độ dễ dàng kéo ra kéo vào vệ sinh.")
                .price(new BigDecimal("75000"))
                .originalPrice(new BigDecimal("165000"))
                .isFree(false)
                .isUrgent(false)
                .itemCondition(ProductCondition.GOOD_85)
                .conditionNote("Mới 90% • Nhựa dày chắc chắn")
                .passReason("Dọn phòng KTX / Chuyển trọ")
                .pickupSpot("Sảnh Tòa BA1 - KTX Khu B")
                .status(ProductStatus.APPROVED)
                .viewCount(53)
                .favoriteCount(7)
                .category(catDecor)
                .university(ktxKhuB)
                .seller(minhThu)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1594620302200-9a762244a156?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        Product p8 = productRepository.save(Product.builder()
                .title("Xe đạp thể thao Martin 107 đi học trong Làng Đại Học")
                .description("Xe đạp sườn nhôm nhẹ, thắng đĩa ăn, lốp mới thay. Đạp từ KTX Khu B qua trường Bách Khoa hay Tự Nhiên chỉ mất 7 phút, tiết kiệm tiền xe buýt. Tặng kèm khóa dây chống trộm.")
                .price(new BigDecimal("450000"))
                .originalPrice(new BigDecimal("1200000"))
                .isFree(false)
                .isUrgent(true)
                .itemCondition(ProductCondition.GOOD_85)
                .conditionNote("Chạy êm • Tặng khóa số")
                .passReason("Lên đời xe máy")
                .pickupSpot("Nhà xe Tòa BA1 - KTX Khu B")
                .status(ProductStatus.APPROVED)
                .viewCount(180)
                .favoriteCount(27)
                .category(catVehicle)
                .university(ktxKhuB)
                .seller(hoangPhuc)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        // Tạo 2 tin đăng ở trạng thái PENDING để Admin kiểm duyệt trên Web Admin
        productRepository.save(Product.builder()
                .title("Máy tính bỏ túi Casio fx-580VN X chính hãng Bitex")
                .description("Máy tính quốc dân cho sinh viên kỹ thuật giải hệ phương trình, ma trận, tích phân siêu nhanh. Nút bấm nhạy, màn hình LCD rõ nét, còn tem chống giả Bitex.")
                .price(new BigDecimal("280000"))
                .originalPrice(new BigDecimal("650000"))
                .isFree(false)
                .isUrgent(false)
                .itemCondition(ProductCondition.LIKE_NEW_95)
                .conditionNote("Mới 96% • Còn tem Bitex")
                .passReason("Học xong môn / Qua môn")
                .pickupSpot("Sảnh thư viện H1 - ĐH Bách Khoa")
                .status(ProductStatus.PENDING)
                .viewCount(12)
                .favoriteCount(2)
                .category(catBooks)
                .university(dhBachKhoa)
                .seller(hoangYen)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1587145820266-a5951ee6f620?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        productRepository.save(Product.builder()
                .title("Bàn phím cơ không dây Keychron K6 Red Switch (Pass nhanh)")
                .description("Bàn phím cơ nhỏ gọn 65% kết nối Bluetooth 3 thiết bị, gõ code cực sướng không gây ồn cho bạn cùng phòng KTX. Đủ cáp Type-C và keycap puller.")
                .price(new BigDecimal("550000"))
                .originalPrice(new BigDecimal("1450000"))
                .isFree(false)
                .isUrgent(true)
                .itemCondition(ProductCondition.LIKE_NEW_95)
                .conditionNote("Mới 95% • Pin trâu 2 tuần")
                .passReason("Lên đời đồ mới")
                .pickupSpot("Sảnh Tòa A - ĐH CNTT (UIT)")
                .status(ProductStatus.PENDING)
                .viewCount(25)
                .favoriteCount(5)
                .category(catElectronics)
                .university(dhCNTT)
                .seller(hoangPhuc)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=800&auto=format&fit=crop&q=80"
                ))
                .build());

        // 5. Tạo dữ liệu Yêu thích, Đơn đặt mua & Tin nhắn Chat mẫu cho Minh Thư
        wishlistRepository.save(Wishlist.builder().user(minhThu).product(p1).build());
        wishlistRepository.save(Wishlist.builder().user(minhThu).product(p3).build());

        orderRepository.save(OrderRequest.builder()
                .product(p1)
                .buyer(minhThu)
                .seller(hoangYen)
                .offeredPrice(new BigDecimal("110000"))
                .meetupLocation("Sảnh Tầng 1 Tòa BA1 - KTX Khu B")
                .meetupTime("17:00 Hôm nay")
                .buyerNote("Chiều 17h mình xuống sảnh BA1 nhận bàn nhé Yến ơi!")
                .status(OrderStatus.PENDING)
                .build());

        chatMessageRepository.save(ChatMessage.builder()
                .sender(minhThu)
                .receiver(hoangYen)
                .product(p1)
                .content("Chào bạn Yến ơi, bàn này bạn còn pass không ạ? Chiều nay mình qua sảnh BA1 xem đồ được không?")
                .messageType("TEXT")
                .isRead(true)
                .build());

        chatMessageRepository.save(ChatMessage.builder()
                .sender(hoangYen)
                .receiver(minhThu)
                .product(p1)
                .content("Chào bạn! Bàn vẫn còn nha, gỗ còn rất chắc chắn không trầy mép đâu. Chiều tầm 17h mình tan tiết ở giảng đường về KTX, hẹn bạn ở sảnh tầng 1 tòa BA1 nha!")
                .messageType("TEXT")
                .isRead(true)
                .build());

        chatMessageRepository.save(ChatMessage.builder()
                .sender(minhThu)
                .receiver(hoangYen)
                .product(p1)
                .content("Phiếu Hẹn Giao Dịch An Toàn: Sảnh Tầng 1 Tòa BA1 lúc 17:00 Hôm nay")
                .messageType("MEETUP")
                .offeredPrice(new BigDecimal("110000"))
                .meetupLocation("Sảnh Tầng 1 Tòa BA1 - KTX Khu B")
                .meetupTime("17:00 Hôm nay")
                .isRead(false)
                .build());

        log.info("Hoàn tất khởi tạo dữ liệu mẫu Chợ Đồ Cũ Sinh Viên (PassUni)!");
    }
}
