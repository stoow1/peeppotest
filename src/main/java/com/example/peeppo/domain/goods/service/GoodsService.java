package com.example.peeppo.domain.goods.service;

import com.amazonaws.services.s3.AmazonS3;
import com.example.peeppo.domain.goods.dto.DeleteResponseDto;
import com.example.peeppo.domain.goods.dto.GoodsRequestDto;
import com.example.peeppo.domain.goods.dto.GoodsResponseDto;
import com.example.peeppo.domain.goods.entity.Goods;
import com.example.peeppo.domain.goods.enums.Category;
import com.example.peeppo.domain.goods.repository.GoodsRepository;
import com.example.peeppo.domain.image.entity.Image;
import com.example.peeppo.domain.image.helper.ImageHelper;
import com.example.peeppo.domain.image.repository.ImageRepository;
import com.example.peeppo.domain.user.entity.User;
import com.example.peeppo.global.responseDto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsService {
    private final GoodsRepository goodsRepository;
    private final ImageRepository imageRepository;
    private final ImageHelper imageHelper;
    private final AmazonS3 amazonS3;
    private final String bucket;

    @Transactional
    public ApiResponse<GoodsResponseDto> goodsCreate(GoodsRequestDto requestDto, List<MultipartFile> images, User user) {
        Goods goods = new Goods(requestDto, user);
        goods.setCategory(Category.getKoreanValueByEnglish(requestDto.getCategory()));
        goodsRepository.save(goods);

        List<String> imageUuids = imageHelper.saveImagesToS3AndRepository(images, amazonS3, bucket, goods);

        return new ApiResponse<>(true, new GoodsResponseDto(goods, imageUuids), null);
    }


    public Page<GoodsResponseDto> allGoods(int page, int size, String sortBy) {
        Pageable pageable = paging(page, size, sortBy);
        Page<Goods> goodsPage = goodsRepository.findAllByIsDeletedFalseOrderByGoodsIdDesc(pageable);
        List<GoodsResponseDto> goodsResponseList = new ArrayList<>();

        for (Goods goods : goodsPage.getContent()) {
            List<Image> images = imageRepository.findByGoodsGoodsId(goods.getGoodsId());
            List<String> imageUrls = new ArrayList<>();
            for (Image image : images) {
                imageUrls.add(image.getImage());
            }
            goodsResponseList.add(new GoodsResponseDto(goods, imageUrls));
        }

        return new PageImpl<>(goodsResponseList, pageable, goodsPage.getTotalElements());
    }

//    public ApiResponse<List<GoodsResponseDto>> locationAllGoods(Long locationId) {
//        List<Goods> goodsList = goodsRepository.findAllByLocationIdAndIsDeletedFalseOrderByGoodsIdDesc(locationId);
//        List<GoodsResponseDto> goodsResponseList = responseDtoList(goodsList);
//
//        return new ApiResponse<>(true, goodsResponseList, null);
//    }


    public ApiResponse<GoodsResponseDto> getGoods(Long goodsId) {
        Goods goods = findGoods(goodsId);
        List<Image> images = imageRepository.findByGoodsGoodsId(goodsId);
        List<String> imageUrls = images.stream()
                .map(Image::getImage)
                .collect(Collectors.toList());

        return new ApiResponse<>(true, new GoodsResponseDto(goods, imageUrls), null);
    }

    @Transactional
    public ApiResponse<GoodsResponseDto> goodsUpdate(Long goodsId, GoodsRequestDto requestDto, List<MultipartFile> images) {
        Goods goods = findGoods(goodsId);

        // repository 이미지 삭제
        List<Image> imagesToDelete = imageHelper.repositoryImageDelete(goodsId);

        // s3 이미지 삭제
        for (Image imageToDelete : imagesToDelete) {
            imageHelper.deleteFileFromS3(imageToDelete.getImageKey(), amazonS3, bucket);
        }

        // 이미지 업로드
        List<String> imageUuids = imageHelper.saveImagesToS3AndRepository(images, amazonS3, bucket, goods);

        goods.setCategory(Category.getKoreanValueByEnglish(requestDto.getCategory()));
        goods.update(requestDto);

        return new ApiResponse<>(true, new GoodsResponseDto(goods, imageUuids), null);
    }

    @Transactional
    public ApiResponse<DeleteResponseDto> deleteGoods(Long goodsId) {
        Goods goods = findGoods(goodsId);
        goods.setDeleted(true);
        goodsRepository.save(goods);

        return new ApiResponse<>(true, new DeleteResponseDto("삭제되었습니다"), null);
    }

    public Goods findGoods(Long goodsId) {
        Goods goods = goodsRepository.findById(goodsId).orElseThrow(() ->
                new NullPointerException("해당 게시글은 존재하지 않습니다."));
        if (goods.isDeleted()) {
            throw new IllegalStateException("삭제된 게시글입니다.");
        }
        return goods;
    }

    private Pageable paging(int page, int size, String sortBy) {
        // 정렬
        Sort sort = Sort.by(sortBy);

        // pageable 생성
        return PageRequest.of(page, size, sort);
    }
}